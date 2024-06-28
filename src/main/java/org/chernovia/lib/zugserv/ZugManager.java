package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * ZugManager extends ZugHandler to handle a variety of common server functions and user interactions.
 */
abstract public class ZugManager extends ZugHandler implements AreaListener, Runnable {
    public static String
            ERR_USER_NOT_FOUND = "User not found",
            ERR_OCCUPANT_NOT_FOUND = "Occupant not found",
            ERR_ROOM_NOT_FOUND = "Room not found",
            ERR_AREA_NOT_FOUND = "Area not found",
            ERR_NOT_OCCUPANT = "Not joined",
            ERR_NO_TITLE = "No title",
            ERR_TITLE_NOT_FOUND = "Title not found";

    /**
     * A repeatedly called process.
     */
    @FunctionalInterface
    public interface ChronJob {
        void begin();
    }

    /**
     * WorkerProc encapsulates a SubProc to repeatedly run at a given interval.
     */
    public static class WorkerProc extends Thread {
        private ChronJob job;
        private long interval;
        private boolean running = false;
        public long getInterval() { return interval; }
        public void setInterval(long interval) { this.interval = interval; }
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }

        /**
         * Creates a worker process with an interval (in millis) and a job to repeatedly execute.
         * @param i the interval (in millis)
         * @param task the task to perform
         */
        public WorkerProc(Long i, ChronJob task) {
            interval = i; job = task; //new Thread(this).start();
        }

        /**
         * Begins the worker process.
         */
        public void run() {
            running = true;
            while (running) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    log(e.getMessage()); running = false;
                }
                job.begin();
            }
        }


    }

    /**
     * Clears defunct areas/users every 30 seconds.
     */
    public WorkerProc cleanupProc = new WorkerProc(30000L, this::cleanup);

    /**
     * Clears defunct areas and users.
     */
    public synchronized void cleanup() {
        areas.values().stream().filter(Timeoutable::timedOut).forEach(area -> { //handle rooms?
            area.spam(ZugFields.ServMsgType.servMsg,"Closing " + area.getTitle() + " (reason: timeout)");
            areaFinished(area);
        });
        users.values().stream().filter(user -> user.timedOut()
                && areas.values().stream().noneMatch(area -> area.getOccupant(user).isPresent())).forEach(user -> {
            log("Removing (idle): " + user.getUniqueName());
            user.getConn().close("User Disconnection/Idle");
            users.remove(user.getUniqueName());
        });
    }

    /**
     * Pings all users every 30 seconds.
     */
    public WorkerProc pingProc = new WorkerProc(30000L, this::pingAll);

    /**
     * Pings all users.
     */
    public synchronized void pingAll() {
        getUsers().values().stream().filter(ZugUser::isLoggedIn).forEach(user -> user.tell(ZugFields.ServMsgType.ping));
    }

    private boolean requirePassword = true;
    private boolean allowGuests = true;

    /**
     * Creates a ZugManager of a given type.
     * @param type a ZugServ type (for example, ZugServ.ServType.TWITCH)
     */
    public ZugManager(ZugServ.ServType type) {
        super(type,0);
    }

    /**
     * Creates a ZugManager of a given type and port.
     * @param type a ZugServ type (for example, ZugServ.ServType.WEBSOCK)
     * @param port a port to listen for incomming connections on
     */
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

    /**
     * Called when a Connection is first established. Default behavior is to then request a login to the server.
     * @param conn The newly created Connection
     */
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

    /**
     * Looks for and returns a ZugArea with the title (ZugFields.TITLE) specified from the top level of a JsonNode.
     * @param dataNode the JSON-formatted data
     * @return an (Optional) ZugArea
     */
    public Optional<ZugArea> getArea(JsonNode dataNode) {
        return getTxtNode(dataNode, ZugFields.TITLE).flatMap(this::getAreaByTitle);
    }

    /**
     * Generates a guest user name with an available numeric suffix.
     * @param name user name (defaults to "guest")
     * @return the appended user name (e.g., guest15, etc.)
     */
    public String generateGuestName(String name) {
        final StringBuilder userName = new StringBuilder(name);
        int i = 0; int l = name.length()+1;
        while (users.values().stream().anyMatch(user -> user.getName().equalsIgnoreCase(userName.toString()))) {
            userName.replace(0,userName.length(),name + (++i));

        }
        return userName.toString();
    }

    /**
     * Handles a great variety of common user actions. If none apply, redirects to handleUnsupportedMsg().
     * @param conn a Connection
     * @param type the message type (as String)
     * @param dataNode the message content (in JSON)
     */
    @Override
    public void handleMsg(Connection conn, String type, JsonNode dataNode) {
        ZugUser user = getUserByConn(conn).orElse(null);
        if (user != null) user.action();
        log(Level.FINEST,"New Message from " + (user == null ? "?" : user.getName()) + ": " + type + "," + dataNode);

        if (!requirePassword && equalsType(type, ZugFields.ClientMsgType.login)) {
            handleLogin(conn, generateGuestName(getTxtNode(dataNode,ZugFields.NAME).orElse("guest")),
                    ZugFields.AuthSource.none,"");
        } else if (allowGuests && equalsType(type, ZugFields.ClientMsgType.loginGuest)) {
            getUsers().values().stream()
                    .filter(u -> u.getSource().equals(ZugFields.AuthSource.none) && u.getConn().getAddress().equals(conn.getAddress()))
                    .findAny().ifPresentOrElse(prevGuest -> swapConnection(prevGuest,conn),
                            () -> handleLogin(conn, generateGuestName(getTxtNode(dataNode,ZugFields.NAME).orElse("guest")),
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

    /**
     * Called by handleMsg for not yet handled messages from a logged in user.
     * @param user the user sending a message
     * @param type the enumerated message type
     * @param dataNode the JSON-formatted message data
     */
    public void handleUserMsg(ZugUser user, String type, JsonNode dataNode) {
        if (equalsType(type, ZugFields.ClientMsgType.servMsg)) {
            handleUserServChat(user,getTxtNode(dataNode,ZugFields.MSG).orElse(""));
        } else if (equalsType(type, ZugFields.ClientMsgType.privMsg)) { //log("Private Message: " + dataNode);
            getUniqueName(dataNode).ifPresentOrElse(uName -> handlePrivateMsg(user,uName,
                            getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                    () -> err(user,"Missing user name"));
        } else if (equalsType(type, ZugFields.ClientMsgType.newArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> err(user, "Already exists: " + title),
                                            () -> handleCreateArea(user, title, dataNode).ifPresent(this::handleAreaCreated)),
                            () -> err(user, ERR_NO_TITLE));
        } else if (equalsType(type, ZugFields.ClientMsgType.joinArea)) {
            getTxtNode(dataNode, ZugFields.TITLE)
                    .ifPresentOrElse(title -> getAreaByTitle(title)
                                    .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                    .ifPresentOrElse(zugArea::rejoin,
                                                            () -> {
                                                                if (zugArea.numOccupants() < zugArea.getMaxOccupants()) {
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
                                                    .ifPresentOrElse(occupant -> handleAreaChat(occupant,getTxtNode(dataNode,ZugFields.MSG).orElse("")),
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
                                    .ifPresentOrElse(occupant -> occupant.update(user.getConn()),
                                               () -> err(user.getConn(), ERR_OCCUPANT_NOT_FOUND)),
                            () -> err(user.getConn(), ERR_AREA_NOT_FOUND));
        } else if (equalsType(type, ZugFields.ClientMsgType.updateUser)) {
            getTxtNode(dataNode, ZugFields.NAME)
                    .ifPresentOrElse(name -> getUserByName(name,getTxtNode(dataNode, ZugFields.SOURCE).orElse(null))
                                    .ifPresentOrElse(usr -> user.update(user.getConn()),
                                            () -> err(user.getConn(), ERR_USER_NOT_FOUND)),
                            () -> user.update(user.getConn()));
        } else if (equalsType(type, ZugFields.ClientMsgType.setMute)) {
            getOccupant(user,dataNode).ifPresent(occupant -> getBoolNode(dataNode,ZugFields.MUTED).ifPresent(occupant::setDeafened));
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

    /**
     * Sends server information to a Connection.
     * @param conn the Connection to update
     */

    public void updateServ(Connection conn) {
        tell(conn, ZugFields.ServMsgType.updateServ,toJSON());
    }

    //public void updateAreas(boolean titleOnly) { spam(ZugFields.ServMsgType.updateAreas,areasToJSON(titleOnly)); }

    private ObjectNode userMsgToJSON(ZugUser user, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.USER,user.toJSON());
    }

    private ObjectNode occupantMsgToJSON(Occupant occupant, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.OCCUPANT, occupant.toJSON());
    }

    /**
     * Gets the Occupant of an area as indicated by a title field (in dataNode) and a ZugUser.
     * @param user the occupying user
     * @param dataNode JSON-formatted data, normally containing ZugFields.TITLE at the top level
     * @return the (Optional) Occupant
     */
    public Optional<Occupant> getOccupant(ZugUser user, JsonNode dataNode) {
        return getArea(dataNode).flatMap(area -> area.getOccupant(user));
    }

    /**
     * Handles a chat message from a user to the general server.
     * @param user the user
     * @param msg the chat message
     */
    public void handleUserServChat(ZugUser user, String msg) {
        spam(ZugFields.ServMsgType.servUserMsg,userMsgToJSON(user,msg));
    }

    /**
     * Handles a chat message from an Occupant to its inhabited area.
     * @param occupant the Occupant
     * @param msg the chat message
     */
    public void handleAreaChat(Occupant occupant, String msg) {
        occupant.getArea().ifPresent(area -> area.spam(ZugFields.ServMsgType.areaUserMsg,occupantMsgToJSON(occupant,msg)));
    }

    /**
     * Handles a private message from one user to another.
     * @param user1 the sender
     * @param name the recipient
     * @param msg the (alphanumeric) message
     */
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

    /**
     * Swaps the Connection of an already logged in user with the newly logged in (presumably duplicate) user.
     * @param prevUser the previous user
     * @param newConn the newly logged in user
     */
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

    /**
     * Gets a UniqueName from JSON-formatted data
     * @param dataNode the JSON-formatted data
     * @return the UniqueName (generally defined by two fields: ZugFields.NAME and ZugFields.SOURCE)
     */
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

    /**
     * Handles the creation (generally the login procedure) of a new user.
     * @param conn the user's Connection
     * @param name the user's alphanumeric name
     * @param source the user's authentication source
     * @param token the user's authentication token (if any)
     * @return an (Optional) newly created user (empty upon failure)
     */
    public abstract Optional<ZugUser> handleCreateUser(Connection conn, String name, ZugFields.AuthSource source, String token);

    /**
     * Handles the creation of a new area.
     * @param user the creator of the area
     * @param title the title of the area
     * @param dataNode JSON-formatted area data
     * @return an (Optional) newly created area (empty upon failure)
     */
    public abstract Optional<ZugArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode);

    /**
     * Handles the creation of a new Occupant, i.e., a user attempting to join an area.
     * @param user the joining user
     * @param area the area to join
     * @param dataNode JSON-formatted "joining" data (if any)
     * @return an (Optional) newly created Occupant (empty upon failure)
     */
    public abstract Optional<Occupant> handleCreateOccupant(ZugUser user, ZugArea area, JsonNode dataNode);

    /**
     * Indicates if an Occupant may leave an area. Defaults to true.
     * @param occupant the departing Occupant
     * @param dataNode JSON-formatted "departing" data (if any)
     * @return true if permitted
     */
    public boolean canPartArea(Occupant occupant, JsonNode dataNode) {
        return true;
    };

    /**
     * Handle any message not yet handled by the ZugManager.
     * @param conn the Connection of the incoming message
     * @param type the enumerated message type
     * @param dataNode the JSON-formatted message data
     * @param user the user asssociated with the Connection (may be null)
     */
    public abstract void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user);

    /**
     * Notifies users of a change in an area.
     * @param area the changed area
     * @param change the enumerated type of change (e.g., ZugFields.AreaChange.created, etc.)
     */
    public void handleAreaListUpdate(ZugArea area, ZugFields.AreaChange change) {
        spam(ZugFields.ServMsgType.updateAreaList,ZugUtils.newJSON()
                .put(ZugFields.AREA_CHANGE,change.name()).set(ZugFields.AREA,area.toJSON(true)));
    }

    private void handleAreaCreated(ZugArea area) {
        addOrGetArea(area);
        areaCreated(area);
    }

    /**
     * Called upon creation of a new area.
     * @param area the newly created Area
     */
    public void areaCreated(ZugArea area) {
        area.getCreator().ifPresent(creator ->
                creator.tell(ZugFields.ServMsgType.createArea, area.toJSON(true))); //TODO: make redundant?
        handleAreaListUpdate(area, ZugFields.AreaChange.created);
    }

    /**
     * Called upon completion of an area.
     * @param area the completed Area
     */
    public void areaFinished(ZugArea area) {
        area.setExistence(false);
        removeArea(area);
        handleAreaListUpdate(area, ZugFields.AreaChange.deleted);
    }

    /**
     * Called upon alteration of an area.
     * @param area the changed Area
     */
    public void areaUpdated(ZugArea area) {
        handleAreaListUpdate(area, ZugFields.AreaChange.updated);
    }

}
