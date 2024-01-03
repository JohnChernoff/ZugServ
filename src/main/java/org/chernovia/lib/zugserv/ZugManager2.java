package org.chernovia.lib.zugserv;

//import net.datafaker.Faker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.logging.Level;

abstract public class ZugManager2 extends ZugManager implements AreaListener, Runnable {
    //public static Faker faker = new Faker();
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
            area.spam(ZugFields.ServTypes.servMsg,"Closing " + area.title + " (reason: timeout)");
            areaFinished(area);
        });
        users.values().stream().filter(user -> user.timedOut()
                && areas.values().stream().noneMatch(area -> area.getOccupant(user).isPresent())).forEach(user -> {
            log("Removing (idle): " + user.uniqueName);
            user.getConn().close("User Disconnection/Idle");
            users.remove(user.getUniqueName());
        });
    }
    boolean requirePassword = true;
    boolean allowGuests = true;

    public ZugManager2(ZugServ.ServType type, int port) {
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
        tell(conn, ZugFields.ServTypes.reqLogin);
    }

    @Override
    public void err(Connection conn, String msg) {
        tell(conn, ZugFields.ServTypes.errMsg, msg);
    }

    @Override
    public void msg(Connection conn, String msg) {
        tell(conn, ZugFields.ServTypes.servMsg, msg);
    }

    public Optional<ZugArea> getArea(JsonNode dataNode) {
        return getTxtNode(dataNode, ZugFields.TITLE).flatMap(this::getAreaByTitle);
    }

    public String generateUserName(String name) {
        final StringBuilder userName = new StringBuilder(name);
        int i = 0;
        while (users.values().stream().anyMatch(user -> user.getName().equalsIgnoreCase(userName.toString()))) {
            userName.replace(0,name.length()+1,name + (++i));
        }
        return userName.toString();
    }

    @Override
    public void handleMsg(Connection conn, String type, JsonNode dataNode) {

        ZugUser user = getUserByConn(conn).orElse(null);
        if (user != null) user.action();
        log(Level.FINEST,"New Message from " + (user == null ? "?" : user.getName()) + ": " + type + "," + dataNode);

        if (!requirePassword && equalsType(type,ZugFields.ClientTypes.login)) {
            handleLogin(conn,generateUserName(getTxtNode(dataNode,ZugFields.NAME).orElse("guest")),
                    ZugFields.AuthSource.none);
        } else if (allowGuests && equalsType(type,ZugFields.ClientTypes.loginGuest)) {
            handleLogin(conn,generateUserName("guest"),ZugFields.AuthSource.none); //TODO: prevent other user accounts as "guest"
        } else if (equalsType(type, ZugFields.ClientTypes.loginLichess)) {
            if (user == null) {
                getTxtNode(dataNode,ZugFields.TOKEN).ifPresentOrElse(
                        token -> handleLichessLogin(conn,token), () -> err(conn,"Empty token"));
            }
            else err(conn,"Already logged in");
        } else if (equalsType(type, ZugFields.ClientTypes.obs)) {
            getArea(dataNode).ifPresent(area -> area.addObserver(conn));
        } else if (equalsType(type, ZugFields.ClientTypes.unObs)) {
            getArea(dataNode).ifPresent(area -> area.removeObserver(conn));
        }
        else if (user != null && equalsType(type, ZugFields.ClientTypes.servMsg)) {
            handleUserServMsg(user,getTxtNode(dataNode,ZugFields.MSG).orElse(""));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.privMsg)) {
            getTxtNode(dataNode,ZugFields.USER).ifPresentOrElse(userName -> handlePrivateMsg(user,userName,
                                            getTxtNode(dataNode, ZugFields.SOURCE).orElse(null),
                                            getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                    () -> err(user,"Missing user name"));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.newArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> err(user, "Already exists: " + title),
                                            () -> handleCreateArea(user, title, dataNode).ifPresent(zugArea -> { addOrGetArea(zugArea); updateAreas(true);  })),
                            () -> err(user, ERR_NO_TITLE));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.joinArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(occupant -> err(user, "Already joined"),
                                                            () -> handleCreateOccupant(user, zugArea, dataNode)), //.ifPresent(occupant -> { zugArea.addOrGetOccupant(occupant); zugArea.updateAll(); })),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.partArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(occupant -> { if (canPartArea(occupant, dataNode)) { zugArea.dropOccupant(occupant); zugArea.updateAll(); }},
                                                            () ->  err(user, ERR_NOT_OCCUPANT)),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.areaMsg)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(occupant -> handleUserAreaMsg(user,zugArea,getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                                                            () ->  err(user, ERR_NOT_OCCUPANT)),
                                            () -> err(user, ERR_TITLE_NOT_FOUND)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type,ZugFields.ClientTypes.updateServ)) {
            updateServ(conn);
        } else if (user != null && equalsType(type,ZugFields.ClientTypes.updateArea)) {
            getArea(dataNode).ifPresent(area -> area.getOccupant(user).ifPresent(area::update));
        } else if (user != null && equalsType(type,ZugFields.ClientTypes.updateOccupant)) {
            String areaTitle = getTxtNode(dataNode,ZugFields.TITLE).orElse("");
            getAreaByTitle(areaTitle)
                    .ifPresentOrElse(area -> area.getOccupant(user)
                                    .ifPresentOrElse(occupant -> updateOccupant(conn, occupant),
                                            () -> err(conn, ERR_OCCUPANT_NOT_FOUND)),
                            () -> err(conn, ERR_AREA_NOT_FOUND));
        } else if (equalsType(type,ZugFields.ClientTypes.updateUser)) {
            getTxtNode(dataNode, ZugFields.NAME)
                    .ifPresentOrElse(name -> getUserByName(name,getTxtNode(dataNode, ZugFields.SOURCE).orElse(null))
                                    .ifPresentOrElse(usr -> updateUser(conn, usr),
                                            () -> err(conn, ERR_USER_NOT_FOUND)),
                            () -> updateUser(conn, user));
        } else if (user != null && equalsType(type, ZugFields.ClientTypes.setMute)) {
            getOccupant(user,dataNode).ifPresent(occupant -> getBoolNode(dataNode,ZugFields.MUTED).ifPresent(occupant::setMuted));
        }
        else handleMsg(conn,type,dataNode,user);
    }

    public void updateServ(Connection conn) {
        tell(conn,ZugFields.ServTypes.updateServ,toJSON());
    }

    public void updateAreas(boolean titleOnly) {
        spam(ZugFields.ServTypes.updateAreas,areasToJSON(titleOnly));
    }

    public void updateUsers(boolean nameOnly) {
        spam(ZugFields.ServTypes.updateUsers,usersToJSON(nameOnly));
    }

    public void updateOccupant(Connection conn, Occupant occupant) { //TODO: move to Occupant
        tell(conn,ZugFields.ServTypes.updateOccupant,occupant.toJSON());
    }

    public void updateUser(Connection conn, ZugUser user) { //TODO: move to ZugUser
        if (user != null) tell(conn,ZugFields.ServTypes.updateUser,user.toJSON());
    }

    public ObjectNode userMsgToJSON(ZugUser user, String msg) {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        node.set(ZugFields.USER, user.toJSON());
        node.put(ZugFields.MSG,msg);
        return node;
    }

    public Optional<Occupant> getOccupant(ZugUser user, JsonNode dataNode) {
        return getArea(dataNode).flatMap(area -> area.getOccupant(user));
    }

    public void handleUserServMsg(ZugUser user, String msg) {
        spam(ZugFields.ServTypes.servUserMsg,userMsgToJSON(user,msg));
    }

    public void handleUserAreaMsg(ZugUser user, ZugArea area, String msg) {
        area.spam(ZugFields.ServTypes.areaUserMsg,userMsgToJSON(user,msg));
    }

    public void handlePrivateMsg(ZugUser user1, String name, String source, String msg) {
        getUserByName(name,source).ifPresentOrElse(user2 -> {
            user2.tell(ZugFields.ServTypes.privMsg,userMsgToJSON(user1,msg));
            user1.tell(ZugFields.ServTypes.servMsg,"Message sent to " + name + ": " + msg);
        }, () -> err(user1,"User not found: " + name));
    }

    public void handleLogin(Connection conn, String name, ZugFields.AuthSource source) {
        handleCreateUser(conn,name,source).ifPresentOrElse(user ->
                        addOrGetUser(user).ifPresentOrElse(prevUser -> {
                            msg(user, "Already logged in, swapping connections");
                            prevUser.setConn(user.conn);
                            handleLoggedIn(prevUser);
                        }, () -> handleLoggedIn(user)),
                () -> err(conn,"Login error"));
    }

    private void handleLoggedIn(ZugUser user) {
        user.loggedIn = true;
        user.tell(ZugFields.ServTypes.logOK,user.toJSON());
        updateUsers(true);
        user.tell(ZugFields.ServTypes.updateAreas,areasToJSON(true));
    }

    public abstract Optional<ZugUser> handleCreateUser(Connection conn, String name, ZugFields.AuthSource source);
    public abstract Optional<ZugArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode);
    public abstract Optional<Occupant> handleCreateOccupant(ZugUser user, ZugArea area, JsonNode dataNode);
    public abstract boolean canPartArea(Occupant occupant, JsonNode dataNode);
    public abstract void handleMsg(Connection conn, String type, JsonNode dataNode, ZugUser user);

    public void areaFinished(ZugArea area) {
        area.exists = false;
        removeArea(area);
        updateAreas(true);
    }

}
