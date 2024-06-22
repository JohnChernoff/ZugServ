package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.logging.Level;

abstract public class ZugManager extends ZugHandler implements AreaListener, Runnable {
    public static String
            ERR_USER_NOT_FOUND = "User not found",
            ERR_OCCUPANT_NOT_FOUND = "Occupant not found",
            ERR_ROOM_NOT_FOUND = "Room not found",
            ERR_AREA_NOT_FOUND = "Area not found",
            ERR_NOT_OCCUPANT = "Not joined",
            ERR_NO_TITLE = "No title",
            ERR_TITLE_NOT_FOUND = "Title not found";

    @FunctionalInterface
    public interface SubProc {
        void begin();
    }
    public static class WorkerProc extends Thread {
        SubProc subProc;
        long interval;
        boolean running = false;
        public WorkerProc(Long t, SubProc s) {
            interval = t; subProc = s; //new Thread(this).start();
        }
        public void run() {
            running = true;
            while (running) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    log(e.getMessage()); running = false;
                }
                subProc.begin();
            }
        }
    }
    public WorkerProc cleanupProc = new WorkerProc(30000L, this::cleanup);
    public synchronized void cleanup() {
        areas.values().stream().filter(Timeoutable::timedOut).forEach(area -> { //handle rooms?
            area.spam(ZugFields.ServMsgType.servMsg,"Closing " + area.title + " (reason: timeout)");
            areaFinished(area);
        });
        users.values().stream().filter(user -> user.timedOut()
                && areas.values().stream().noneMatch(area -> area.getOccupant(user).isPresent())).forEach(user -> {
            log("Removing (idle): " + user.getUniqueName());
            user.getConn().close("User Disconnection/Idle");
            users.remove(user.getUniqueName());
        });
    }

    public WorkerProc pingProc = new WorkerProc(30000L, this::pingAll);
    public synchronized void pingAll() {
        getUsers().values().stream().filter(ZugUser::isLoggedIn).forEach(user -> user.tell(ZugFields.ServMsgType.ping));
    }

    boolean requirePassword = true;
    boolean allowGuests = true;

    public ZugManager(ZugServ.ServType type, int port) {
        super(type,port);
    }

    public boolean requiresPassword() {
        return requirePassword;
    }

    public void setRequirePassword(boolean bool) {
        requirePassword = bool;
    }

    public boolean allowingGuests() {
        return allowGuests;
    }

    public void setAllowGuests(boolean bool) {
        allowGuests = bool;
    }

    @Override
    public void connected(Connection conn) {
        tell(conn, ZugFields.ServMsgType.reqLogin,ZugUtils.newJSON().put(ZugFields.ID,conn.getID()));
    }

    @Override
    public void err(Connection conn, String msg) {
        tell(conn, ZugFields.ServMsgType.errMsg, msg);
    }

    @Override
    public void msg(Connection conn, String msg) {
        tell(conn, ZugFields.ServMsgType.servMsg, msg);
    }

    public Optional<ZugArea> getArea(JsonNode dataNode) {
        return getTxtNode(dataNode, ZugFields.TITLE).flatMap(this::getAreaByTitle);
    }

    public String generateUserName(String name) {
        final StringBuilder userName = new StringBuilder(name);
        int i = 0; int l = name.length()+1;
        while (users.values().stream().anyMatch(user -> user.getName().equalsIgnoreCase(userName.toString()))) {
            userName.replace(0,userName.length(),name + (++i));

        }
        return userName.toString();
    }

    @Override
    public void handleMsg(Connection conn, String type, JsonNode dataNode) {
        ZugUser user = getUserByConn(conn).orElse(null);
        if (user != null) user.action();
        log(Level.FINEST,"New Message from " + (user == null ? "?" : user.getName()) + ": " + type + "," + dataNode);

        if (!requirePassword && equalsType(type, ZugFields.ClientMsgType.login)) {
            handleLogin(conn,generateUserName(getTxtNode(dataNode,ZugFields.NAME).orElse("guest")),
                    ZugFields.AuthSource.none,"");
        } else if (allowGuests && equalsType(type, ZugFields.ClientMsgType.loginGuest)) {
            getUsers().values().stream()
                    .filter(u -> u.getSource().equals(ZugFields.AuthSource.none) && u.getConn().getAddress().equals(conn.getAddress()))
                    .findAny().ifPresentOrElse(prevGuest -> swapConnection(prevGuest,conn),
                            () -> handleLogin(conn,generateUserName(getTxtNode(dataNode,ZugFields.NAME).orElse("guest")),
                                    ZugFields.AuthSource.none,""));
        } else if (equalsType(type, ZugFields.ClientMsgType.loginLichess)) {
            if (user == null) {
                getTxtNode(dataNode,ZugFields.TOKEN).ifPresentOrElse(
                        token -> handleLichessLogin(conn,token), () -> err(conn,"Empty token"));
            }
            else err(conn,"Already logged in");
        } else if (equalsType(type, ZugFields.ClientMsgType.ip)) {
            getTxtNode(dataNode, ZugFields.ADDRESS).ifPresent(addressStr -> {
                        try {
                            conn.setAddress(InetAddress.getByName(addressStr));
                            log("Incoming address: " + conn.getAddress());
                        }
                        catch (UnknownHostException oops) { log("Unknown Host: " + addressStr); }
                    }
            );
            tell(conn,ZugFields.ServMsgType.ip,ZugUtils.newJSON().put(ZugFields.ADDRESS,conn.getAddress().toString()));
        } else if (equalsType(type, ZugFields.ClientMsgType.obs)) { log(Level.FINE,"Obs requested from: " + conn.getID());
            getArea(dataNode).ifPresent(area -> area.addObserver(conn));
        } else if (equalsType(type, ZugFields.ClientMsgType.unObs)) {  log(Level.FINE,"UnObs requested from: " + conn.getID());
            getArea(dataNode).ifPresent(area -> area.removeObserver(conn));
        } else if (user != null) handleUserMsg(user,type,dataNode);
        else { //err(conn,"Please login first");
            handleUnsupportedMsg(conn,type,dataNode,null);
        }
    }

    public void handleUserMsg(ZugUser user, String type, JsonNode dataNode) {
        if (equalsType(type, ZugFields.ClientMsgType.servMsg)) {
            handleUserServMsg(user,getTxtNode(dataNode,ZugFields.MSG).orElse(""));
        } else if (equalsType(type, ZugFields.ClientMsgType.privMsg)) { //log("Private Message: " + dataNode);
            getUniqueName(dataNode).ifPresentOrElse(uName -> handlePrivateMsg(user,uName,
                            getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                    () -> err(user,"Missing user name"));
        } else if (equalsType(type, ZugFields.ClientMsgType.newArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> err(user, "Already exists: " + title),
                                            () -> handleCreateArea(user, title, dataNode).ifPresent(zugArea -> createArea(zugArea,user))),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type, ZugFields.ClientMsgType.joinArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(zugArea::rejoin,
                                                            () -> {
                                                                if (zugArea.occupants.size() < zugArea.getMaxOccupants()) {
                                                                    handleCreateOccupant(user, zugArea, dataNode);
                                                                            //.ifPresent(occupant -> occupant.joinArea(zugArea));
                                                                }
                                                                else err(user,"Game full: " + title);
                                                            }),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type, ZugFields.ClientMsgType.partArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(occupant -> { if (canPartArea(occupant, dataNode)) { zugArea.dropOccupant(occupant); zugArea.updateOccupants(); }},
                                                            () ->  err(user, ERR_NOT_OCCUPANT)),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type, ZugFields.ClientMsgType.areaMsg)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(occupant -> handleAreaMsg(occupant,zugArea,getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                                                            () ->  err(user, ERR_NOT_OCCUPANT)),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type, ZugFields.ClientMsgType.updateServ)) {
            updateServ(user.getConn());
        } else if (equalsType(type, ZugFields.ClientMsgType.updateArea)) {
            getArea(dataNode).ifPresent(area -> {
                        if (!area.isPrivate()) area.update(user);
                        else getOccupant(user,dataNode).ifPresent(occupant -> area.update(occupant.getUser()));
                    }
            );
        } else if (equalsType(type, ZugFields.ClientMsgType.updateOccupant)) {
            String areaTitle = getTxtNode(dataNode,ZugFields.TITLE).orElse("");
            getAreaByTitle(areaTitle)
                    .ifPresentOrElse(area -> area.getOccupant(user)
                                    .ifPresentOrElse(occupant -> updateOccupant(user.getConn(), occupant),
                                            () -> err(user.getConn(), ERR_OCCUPANT_NOT_FOUND)),
                            () -> err(user.getConn(), ERR_AREA_NOT_FOUND));
        } else if (equalsType(type, ZugFields.ClientMsgType.updateUser)) {
            getTxtNode(dataNode, ZugFields.NAME)
                    .ifPresentOrElse(name -> getUserByName(name,getTxtNode(dataNode, ZugFields.SOURCE).orElse(null))
                                    .ifPresentOrElse(usr -> updateUser(user.getConn(), usr),
                                            () -> err(user.getConn(), ERR_USER_NOT_FOUND)),
                            () -> updateUser(user.getConn(), user));
        } else if (equalsType(type, ZugFields.ClientMsgType.setMute)) {
            getOccupant(user,dataNode).ifPresent(occupant -> getBoolNode(dataNode,ZugFields.MUTED).ifPresent(occupant::setMuted));
        } else if (equalsType(type, ZugFields.ClientMsgType.ban)) {
            getArea(dataNode).ifPresent(area -> getOccupant(user, dataNode)
                    .flatMap(occupant -> getUniqueName(dataNode.get(ZugFields.NAME)))
                    .ifPresent(name -> area.banOccupant(user, name, 15 * 60 * 1000,true)));
        } else if (equalsType(type, ZugFields.ClientMsgType.getOptions)) {
            getArea(dataNode).ifPresent(area -> area.updateOptions(user));
        } else if (equalsType(type, ZugFields.ClientMsgType.setOptions)) {
            getArea(dataNode).ifPresent(area -> getJSONNode(dataNode,ZugFields.OPTIONS).ifPresent(options -> area.setOptions(user,options)));
        }
        else handleUnsupportedMsg(user.getConn(),type,dataNode,user);
    }

    public void updateServ(Connection conn) {
        tell(conn, ZugFields.ServMsgType.updateServ,toJSON());
    }

    //public void updateAreas(boolean titleOnly) { spam(ZugFields.ServMsgType.updateAreas,areasToJSON(titleOnly)); }

    public void updateOccupant(Connection conn, Occupant occupant) { //TODO: move to Occupant
        tell(conn, ZugFields.ServMsgType.updateOccupant,occupant.toJSON());
    }

    public void updateUser(Connection conn, ZugUser user) { //TODO: move to ZugUser
        if (user != null) tell(conn, ZugFields.ServMsgType.updateUser,user.toJSON());
    }

    public ObjectNode userMsgToJSON(ZugUser user, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.USER,user.toJSON());
    }

    public ObjectNode occupantMsgToJSON(Occupant occupant, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.OCCUPANT, occupant.toJSON());
    }

    public Optional<Occupant> getOccupant(ZugUser user, JsonNode dataNode) {
        return getArea(dataNode).flatMap(area -> area.getOccupant(user));
    }

    public void handleUserServMsg(ZugUser user, String msg) {
        spam(ZugFields.ServMsgType.servUserMsg,userMsgToJSON(user,msg));
    }

    public void handleAreaMsg(Occupant occupant, ZugArea area, String msg) {
        area.spam(ZugFields.ServMsgType.areaUserMsg,occupantMsgToJSON(occupant,msg));
    }

    public void handlePrivateMsg(ZugUser user1, ZugUser.UniqueName name, String msg) { //log("Handling privMsg to: " + name);
        getUserByUniqueName(name).ifPresentOrElse(user2 -> {
            user2.tell(ZugFields.ServMsgType.privMsg,userMsgToJSON(user1,msg));
            user1.tell(ZugFields.ServMsgType.servMsg,"Message sent to " + name + ": " + msg);
        }, () -> err(user1,"User not found: " + name));
    }

    @Override
    public void handleLogin(Connection conn, String name, ZugFields.AuthSource source, String token) {
        handleCreateUser(conn,name,source,token).ifPresentOrElse(user ->
                        addOrGetUser(user).ifPresentOrElse(prevUser -> swapConnection(prevUser,user.getConn()),
                                () -> handleLoggedIn(user)),
                () -> err(conn,"Login error"));
    }

    public void swapConnection(ZugUser prevUser, Connection newConn) {
        newConn.tell(ZugFields.ServMsgType.alertMsg,"Already logged in, swapping connections");
        prevUser.setConn(newConn);
        handleLoggedIn(prevUser);
    }

    private void handleLoggedIn(ZugUser user) {
        user.setLoggedIn(true);
        user.tell(ZugFields.ServMsgType.logOK,user.toJSON());
        user.tell(ZugFields.ServMsgType.areaList,areasToJSON(true));
        //spam(ZugFields.ServMsgType.userList,usersToJSON(true));
    }

    public Optional<ZugUser.UniqueName> getUniqueName(JsonNode dataNode) {
        String source = getTxtNode(dataNode,ZugFields.SOURCE)
                .orElse(getTxtNode(dataNode.get(ZugFields.NAME),ZugFields.SOURCE)
                        .orElse(""));
        String name = getTxtNode(dataNode,ZugFields.NAME)
                .orElse(getTxtNode(dataNode.get(ZugFields.NAME),ZugFields.NAME)
                        .orElse(""));
        try {
            return Optional.of(new ZugUser.UniqueName(name,
                    ZugFields.AuthSource.valueOf(source)));
        }
        catch (IllegalArgumentException arg) {
            return Optional.empty(); //new ZugUser.UniqueName(name, ZugFields.AuthSource.none);
        }
    }

    public abstract Optional<ZugUser> handleCreateUser(Connection conn, String name, ZugFields.AuthSource source, String token);
    public abstract Optional<ZugArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode);
    public abstract Optional<Occupant> handleCreateOccupant(ZugUser user, ZugArea area, JsonNode dataNode);
    public boolean canPartArea(Occupant occupant, JsonNode dataNode) { //why does this exist? :)
        return true;
    };
    public abstract void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user);
    public void handleAreaListUpdate(ZugArea area, ZugFields.AreaChange change) {
        spam(ZugFields.ServMsgType.updateAreaList,ZugUtils.newJSON()
                .put(ZugFields.AREA_CHANGE,change.name()).set(ZugFields.AREA,area.toJSON(true)));
    }

    public void createArea(ZugArea area, ZugUser creator) {
        addOrGetArea(area);
        areaCreated(area);
        creator.tell(ZugFields.ServMsgType.createArea, area.toJSON(true)); //TODO: make redundant?
    }

    public void areaFinished(ZugArea area) {
        area.exists = false;
        removeArea(area);
        handleAreaListUpdate(area, ZugFields.AreaChange.deleted);
    }

    public void areaCreated(ZugArea area) {
        handleAreaListUpdate(area, ZugFields.AreaChange.created);
    }

    public void areaUpdated(ZugArea area) {
        handleAreaListUpdate(area, ZugFields.AreaChange.updated);
    }

}
