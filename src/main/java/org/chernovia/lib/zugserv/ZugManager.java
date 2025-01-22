package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import net.datafaker.*;

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
     * WorkerProc encapsulates a ChronJob to repeatedly run at a given interval.
     */
    public static class WorkerProc extends Thread {
        private final ChronJob job;
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
        users.values().stream().filter(user -> user.timedOut() && getAreasByUser(user).isEmpty()).forEach(user -> {
            log("Removing (idle): " + user.getUniqueName());
            user.getConn().close("User Disconnection/Idle");
            users.remove(user.getUniqueName().toString());
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
    private boolean swapGuestConnection = false;
    private boolean fancyGuestNames = true;
    private final List<Class<? extends Enum<?>>> commandList = new ArrayList<>();
    private int crowdThreshold = 100;
    private static final AtomicLong idCounter = new AtomicLong();
    public static String createID() {
        return String.valueOf(idCounter.getAndIncrement());
    }
    public static String areaName = "Area";

    @FunctionalInterface
    public interface CommandHandler {
        void handleCommand(ZugUser user,JsonNode data);
    }

    private final Map<Enum<?>,CommandHandler> handMap = new HashMap<>();

    /**
     * Creates a ZugManager of a given type.
     * @param type a ZugServ type (for example, ZugServ.ServType.TWITCH)
     */
    public ZugManager(ZugServ.ServType type) {
        this(type,0);
    }

    /**
     * Creates a ZugManager of a given type and port.
     * @param type a ZugServ type (for example, ZugServ.ServType.WEBSOCK)
     * @param port a port to listen for incomming connections on
     */
    public ZugManager(ZugServ.ServType type, int port) {
        super(type,port);
        addMessageList(ZugFields.ClientMsgType.class);
        addHandler(ZugFields.ClientMsgType.newArea,this::handleCreateArea);
        addHandler(ZugFields.ClientMsgType.joinArea,this::handleJoinArea);
        addHandler(ZugFields.ClientMsgType.partArea,this::handlePartArea);
        addHandler(ZugFields.ClientMsgType.startArea,this::handleStartArea);

        addHandler(ZugFields.ClientMsgType.servMsg,this::handleServerMessage);
        addHandler(ZugFields.ClientMsgType.privMsg,this::handlePrivateMessage);
        addHandler(ZugFields.ClientMsgType.areaMsg,this::handleAreaMsg);

        addHandler(ZugFields.ClientMsgType.updateServ,this::handleUpdateServ);
        addHandler(ZugFields.ClientMsgType.updateArea,this::handleUpdateArea);
        addHandler(ZugFields.ClientMsgType.updateOccupant,this::handleUpdateOccupant);
        addHandler(ZugFields.ClientMsgType.updateUser,this::handleUpdateUser);

        addHandler(ZugFields.ClientMsgType.setDeaf,this::handleDeafen);
        addHandler(ZugFields.ClientMsgType.ban,this::handleBan);
        addHandler(ZugFields.ClientMsgType.getOptions,this::handleUpdateOptions);
        addHandler(ZugFields.ClientMsgType.setOptions,this::handleSetOptions);
    }

    public boolean requiringPassword() {
        return requirePassword;
    }

    public boolean swappingGuestConnection() {
        return swapGuestConnection;
    }

    public void setSwapGuestConnection(boolean swapGuestConnection) {
        this.swapGuestConnection = swapGuestConnection;
    }

    public boolean usingFancyGuestNames() {
        return fancyGuestNames;
    }

    public void setFancyGuestNames(boolean fancyGuestNames) {
        this.fancyGuestNames = fancyGuestNames;
    }

    public int getCrowdThreshold() {
        return crowdThreshold;
    }

    public void setCrowdThreshold(int n) {
        crowdThreshold = n;
    }

    public void addMessageList(Class<? extends Enum<?>> e) {
        commandList.add(e);
    }

    public void addHandler(Enum<?> e, CommandHandler handler) {
        handMap.put(e,handler);
    }

    /**
     * Handles a variety of common user actions. If none apply, redirects to handleUnsupportedMsg().
     * @param conn a Connection
     * @param type the message type (as String)
     * @param dataNode the message content (in JSON)
     */
    @Override
    public void handleMsg(Connection conn, String type, JsonNode dataNode) {
        ZugUser user = getUserByConn(conn).orElse(null);
        if (user != null) user.action();
        log(Level.FINE,"New Message from " + (user == null ? "?" : user.getName()) + ": " + type + "," + dataNode);

        if (equalsType(type, ZugFields.ClientMsgType.login)) {
            if (user != null) err(conn,"Already logged in");
            else handleLoginRequest(conn,dataNode);
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
        List<CommandHandler> handleList = new ArrayList<>();
        commandList.forEach(cmdSet -> {
            try {
                Arrays.stream(cmdSet.getEnumConstants()).filter(eCon -> eCon.name().equalsIgnoreCase(type)).forEach(e -> {
                    CommandHandler handler = handMap.get(e);
                    if (handler != null) {
                        handler.handleCommand(user,dataNode);
                        handleList.add(handler);
                    }
                });
            }
            catch (IllegalArgumentException ignore) { //err(user,"No such command type: " + type);
            }
        });
        if (handleList.isEmpty()) {
            handleUnsupportedMsg(user.getConn(),type,dataNode,user);
        }
    }

    /* *** */

    public void handleServerMessage(ZugUser user, JsonNode dataNode) {
        String msg = getTxtNode(dataNode,ZugFields.MSG).orElse("");
        spam(ZugFields.ServMsgType.servUserMsg,userMsgToJSON(user,msg));
    }

    public void handlePrivateMessage(ZugUser user, JsonNode dataNode) {
        getUniqueName(dataNode).ifPresentOrElse(uName -> sendPrivateMsg(user,uName,
                        getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                () -> err(user,"Missing user name"));
    }

    public void handleCreateArea(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.TITLE)
                .ifPresentOrElse(title -> getAreaByTitle(title)
                                .ifPresentOrElse(zugArea -> err(user, "Already exists: " + title),
                                        () -> handleCreateArea(user, title.isBlank() ? createID() : title, dataNode)
                                                .ifPresent(area -> handleAreaCreated(area,true))), //TODO: user dataNode for autojoin
                        () -> handleCreateArea(user, areaName + createID(), dataNode).ifPresent(area -> handleAreaCreated(area,true))
                );
    }

    public void handleJoinArea(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.TITLE)
                .ifPresentOrElse(title -> getAreaByTitle(title)
                                .ifPresentOrElse(zugArea -> joinArea(zugArea,user,title,dataNode),
                                        () -> err(user, ERR_TITLE_NOT_FOUND + ": " + title)),
                        () -> handleJoinRandomArea(user,dataNode));
    }

    public void handleJoinRandomArea(ZugUser user, JsonNode dataNode) { //default is to join the area with the most users
        areas.values().stream().filter(ZugArea::isOpen).sorted().findFirst()
                .ifPresentOrElse(area -> handleCreateOccupant(user, area, dataNode)
                                .ifPresent(occupant -> joinArea(area,occupant))
                        , () -> handleCreateArea(user,dataNode));
    }

    public void handlePartArea(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.TITLE)
                .ifPresentOrElse(title -> getAreaByTitle(title)
                                .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                .ifPresentOrElse(occupant -> { if (canPartArea(zugArea,occupant, dataNode)) {
                                                    if (zugArea.dropOccupant(occupant)) {
                                                        user.tell(ZugFields.ServMsgType.partArea,ZugUtils.newJSON().put(ZugFields.TITLE,zugArea.getTitle()));
                                                        areaUpdated(zugArea);
                                                    }
                                                }}, () ->  err(user, ERR_NOT_OCCUPANT)),
                                        () -> err(user, ERR_TITLE_NOT_FOUND)),
                        () -> err(user, ERR_NO_TITLE));
    }

    public void handleStartArea(ZugUser user, JsonNode dataNode) {
        getArea(dataNode).ifPresentOrElse(area -> {
            if (area.startArea(user,dataNode)) areaUpdated(area);
        }, () -> err(user,"Area not found"));
    }

    public void handleAreaMsg(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.TITLE)
                .ifPresentOrElse(title -> getAreaByTitle(title)
                                .ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                                                .ifPresentOrElse(occupant -> sendAreaChat(occupant,getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                                                        () ->  err(user, ERR_NOT_OCCUPANT)),
                                        () -> err(user, ERR_TITLE_NOT_FOUND)),
                        () -> err(user, ERR_NO_TITLE));
    }

    public void handleUpdateServ(ZugUser user, JsonNode dataNode) {
        updateServ(user.getConn());
    }

    public void handleUpdateArea(ZugUser user, JsonNode dataNode) {
        getArea(dataNode).ifPresent(area -> {
                    if (!area.isPrivate()) area.update(user);
                    else getOccupant(user,dataNode).ifPresent(occupant -> area.update(occupant.getUser()));
                }
        );
    }

    public void handleUpdateOccupant(ZugUser user, JsonNode dataNode) {
        String areaTitle = getTxtNode(dataNode,ZugFields.TITLE).orElse("");
        getAreaByTitle(areaTitle)
                .ifPresentOrElse(area -> area.getOccupant(user)
                                .ifPresentOrElse(occupant -> occupant.update(user.getConn()),
                                        () -> err(user.getConn(), ERR_OCCUPANT_NOT_FOUND)),
                        () -> err(user.getConn(), ERR_AREA_NOT_FOUND));
    }

    public void handleUpdateUser(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.NAME)
                .ifPresentOrElse(name -> getUserByName(name,getTxtNode(dataNode, ZugFields.SOURCE).orElse(null))
                                .ifPresentOrElse(usr -> user.update(user.getConn()),
                                        () -> err(user.getConn(), ERR_USER_NOT_FOUND)),
                        () -> user.update(user.getConn()));
    }


    public void handleDeafen(ZugUser user, JsonNode dataNode) {
        getOccupant(user,dataNode).ifPresent(occupant -> getBoolNode(dataNode,ZugFields.DEAFENED).ifPresent(occupant::setDeafened));
    }


    public void handleBan(ZugUser user, JsonNode dataNode) {
        getArea(dataNode).ifPresent(area -> getOccupant(user, dataNode)
                .flatMap(occupant -> getUniqueName(dataNode.get(ZugFields.NAME)))
                .ifPresent(name -> area.banOccupant(user, name, 15 * 60 * 1000,true)));
    }

    public void handleUpdateOptions(ZugUser user, JsonNode dataNode) {
        getArea(dataNode).ifPresent(area -> area.updateOptions(user));
    }

    public void handleSetOptions(ZugUser user, JsonNode dataNode) {
        getArea(dataNode).ifPresent(area -> getJSONNode(dataNode,ZugFields.OPTIONS).ifPresent(options -> area.setOptions(user,options)));
    }

    /* *** */

    private ObjectNode userMsgToJSON(ZugUser user, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.USER,user.toJSON());
    }

    private ObjectNode occupantMsgToJSON(Occupant occupant, String msg) {
        return ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.OCCUPANT,occupant.toJSON());
    }

    private void handleAreaCreated(ZugArea area, boolean join) {
        addOrGetArea(area);
        if (join) area.getCreator().ifPresent(creator -> joinArea(area,creator,area.getTitle()));
        areaCreated(area);
    }

    private void joinArea(ZugArea area, ZugUser user, String title) { joinArea(area,user,title,ZugUtils.newJSON()); }
    private void joinArea(ZugArea area, ZugUser user, String title, JsonNode dataNode) {
        area.getOccupant(user).ifPresentOrElse(area::rejoin, () -> {
            if (area.numOccupants() < area.getMaxOccupants()) { //TODO: handle errors
                handleCreateOccupant(user, area, dataNode).ifPresent(occupant -> joinArea(area,occupant));
            }
            else err(user,"Game full: " + title);
        });
    }

    private void joinArea(ZugArea area, Occupant occupant) {
        if (!occupant.isClone()) {
            if (area.addOccupant(occupant)) {
                occupant.tell(ZugFields.ServMsgType.joinArea,area.toJSON(true));
                areaUpdated(area);
            }
        }
    }

    /* *** */

    /**
     * Sends server information to a Connection.
     * @param conn the Connection to update
     */
    public void updateServ(Connection conn) {
        tell(conn, ZugFields.ServMsgType.updateServ,toJSON());
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
     * Sends a chat message from an Occupant to its inhabited area.
     * @param occupant the Occupant
     * @param msg the chat message
     */
    public void sendAreaChat(Occupant occupant, String msg) {
        occupant.getArea().ifPresentOrElse(area ->
                        area.spam(ZugFields.ServMsgType.areaUserMsg,occupantMsgToJSON(occupant,msg)),
                () -> err(occupant.getUser(),"Area not found"));
    }

    /**
     * Sends a private message from one user to another.
     * @param user1 the sender
     * @param name the recipient
     * @param msg the (alphanumeric) message
     */
    public void sendPrivateMsg(ZugUser user1, ZugUser.UniqueName name, String msg) { //log("Handling privMsg to: " + name);
        getUserByUniqueName(name).ifPresentOrElse(user2 -> {
            user2.tell(ZugFields.ServMsgType.privMsg,userMsgToJSON(user1,msg));
            user1.tell(ZugFields.ServMsgType.servMsg,"Message sent to " + name + ": " + msg);
        }, () -> err(user1,"User not found: " + name));
    }


    /**
     * Handles the beginning of a login request.
     * @param conn the connection that will, upon a successful login, become associated with a ZugUser
     * @param dataNode login data
     */
    public void handleLoginRequest(Connection conn, JsonNode dataNode) {
        try {
            ZugFields.AuthSource source =
                    ZugFields.AuthSource.valueOf(dataNode.get(ZugFields.LOGIN_TYPE).textValue().toLowerCase());
            if (source == ZugFields.AuthSource.lichess) {
                getTxtNode(dataNode,ZugFields.TOKEN).ifPresentOrElse(
                        token -> handleLichessLogin(conn,token), () -> err(conn,"Empty token"));
            }
            else if (source == ZugFields.AuthSource.none) {
                if (allowGuests) handleLogin(
                        conn,
                        generateGuestName(getTxtNode(dataNode,ZugFields.NAME).orElse(ZugFields.GUEST)),
                        dataNode);
                else err(conn,"Login error: guests not allowed");
            }
            else err(conn,"Login error: source not found");
        }
        catch (IllegalArgumentException e) {
            err(conn,"Login error: bad source");
        }
    }

    @Override
    public void handleLogin(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode) {
        log("Handling Login: " + uName);
        getUsers().values().stream()
                .filter(user -> user.sameUser(uName,conn)).findFirst()
                .ifPresentOrElse(prevUser -> swapConnection(prevUser,conn),
                () -> handleCreateUser(conn,uName,dataNode)
                        .ifPresentOrElse(newUser -> addOrGetUser(newUser)
                                        .ifPresentOrElse(wtf -> err(conn,"Error: duplicate user!"),
                                                () -> handleLoggedIn(newUser)),
                        () -> err(conn,"Login error")));
    }


    /**
     * Called upon completion of a successful login.
     * @param user The newly created (or connection-swapped) ZugUser
     */
    public void handleLoggedIn(ZugUser user) {
        user.setLoggedIn(true);
        user.tell(ZugFields.ServMsgType.logOK,user.toJSON());
        user.tell(ZugFields.ServMsgType.areaList,areasToJSON(true,isCrowded() ? user : null));
    }

    /**
     * Swaps the Connection of an already logged in user with the newly logged in (presumably duplicate) user.
     * @param prevUser the previous user
     * @param newConn the newly logged in user
     */
    public void swapConnection(ZugUser prevUser, Connection newConn) {
        newConn.tell(ZugFields.ServMsgType.servMsg,"Already logged in, swapping connections");
        prevUser.setConn(newConn);
        handleLoggedIn(prevUser);
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
     * @param uName the user's UniqueName
     * @param dataNode extra JSON-formatted user data (if any)
     * @return an (Optional) newly created user (empty upon failure)
     */
    public abstract Optional<ZugUser> handleCreateUser(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode);

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
     * @param area the occupied area
     * @param occupant the departing Occupant
     * @param dataNode JSON-formatted "departing" data (if any)
     * @return true if permitted
     */
    public boolean canPartArea(ZugArea area, Occupant occupant, JsonNode dataNode) {
        return true;
    }

    /**
     * Handle any message not yet handled by the ZugManager.
     * @param conn the Connection of the incoming message
     * @param type the enumerated message type
     * @param dataNode the JSON-formatted message data
     * @param user the user asssociated with the Connection (may be null)
     */
    public abstract void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user);

    /**
     * Notifies users of a change in an area. Does nothing while isCrowded() is true.
     * @param area the changed area
     * @param change the enumerated type of change (e.g., ZugFields.AreaChange.created, etc.)
     */
    public void handleAreaListUpdate(ZugArea area, ZugFields.AreaChange change) {
        if (!isCrowded() && area.exists()) {
            spam(ZugFields.ServMsgType.updateAreaList,ZugUtils.newJSON()
                    .put(ZugFields.AREA_CHANGE,change.name()).set(ZugFields.AREA,area.toJSON(true)));
        }
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
        handleAreaListUpdate(area, ZugFields.AreaChange.deleted);
        area.setExistence(false);
        removeArea(area);
    }

    /**
     * Called upon alteration of an area.
     * @param area the changed Area
     */
    public void areaUpdated(ZugArea area) {
        handleAreaListUpdate(area, ZugFields.AreaChange.updated);
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

    public boolean isCrowded() {
        return users.values().size() > crowdThreshold;
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

    @Override
    public ObjectNode toJSON() {
        return isCrowded() ? ZugUtils.newJSON().put("crowded",true) : super.toJSON().put("crowded",false);
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
     * Generates a guest user name.
     * @param name user name
     * @return the guest's UniqueName
     */
    public ZugUser.UniqueName generateGuestName(String name) {
        if (fancyGuestNames && name.equals(ZugFields.GUEST)) {
            name = new Faker().artist().name().replace(" ","") + new Faker().animal().name();
        }
        final StringBuilder userName = new StringBuilder(name);
        int i = 0; //int l = name.length()+1;
        while (users.values().stream().anyMatch(user -> user.getName().equalsIgnoreCase(userName.toString()))) {
            userName.replace(0,userName.length(),name + (++i));

        }
        return new ZugUser.UniqueName(userName.toString(), ZugFields.AuthSource.none);
    }

}
