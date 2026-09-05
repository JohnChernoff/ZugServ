package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.MonthDay;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import net.datafaker.*;
import org.chernovia.lib.zugserv.enums.*;

/**
 * ZugManager extends ZugHandler to handle a variety of common server functions and user interactions.
 * Parameterized by the concrete Occupant type (T) and concrete Area type (A) this manager handles —
 * every real subclass (FL_Serv, BingoServ, etc.) manages exactly one area/occupant pair.
 */
abstract public class ZugManager<O extends Occupant, A extends ZugArea<O>> extends ZugHandler<A,O> implements AreaListener<O,A> {

    public int maxMsgLen = 512;

    public static String
            ERR_USER_NOT_FOUND = "User not found",
            ERR_OCCUPANT_NOT_FOUND = "Occupant not found",
            ERR_ROOM_NOT_FOUND = "Room not found",
            ERR_AREA_NOT_FOUND = "Area not found",
            ERR_NOT_OCCUPANT = "Not joined",
            ERR_NO_TITLE = "No title",
            ERR_TITLE_NOT_FOUND = "Title not found";

    @FunctionalInterface
    public interface ChronJob {
        void begin();
    }

    public static class WorkerProc extends Thread {
        private final ChronJob job;
        private long interval;
        private boolean running = false;
        private volatile Throwable lastError = null;

        public Throwable getLastError() { return lastError; }

        public WorkerProc(Long i, ChronJob task) {
            interval = i;
            job = task;
            setName("WorkerProc-" + System.nanoTime());
            setUncaughtExceptionHandler((t, e) -> {
                ZugHandler.log(Level.SEVERE,
                        "Worker thread " + t.getName() + " crashed: " + e.getMessage());
                ZugServ.printStackTrace(e);
                lastError = e;
            });
        }

        public void run() {
            running = true;
            ZugHandler.log(Level.INFO, "Worker started: " + getName());
            while (running) {
                try {
                    Thread.sleep(interval);
                    try {
                        job.begin();
                    } catch (Exception e) {
                        ZugHandler.log(Level.SEVERE, "Job " + getName() + " failed: " + e.getMessage());
                        ZugServ.printStackTrace(e);
                        lastError = e;
                    }
                } catch (InterruptedException e) {
                    if (running) {
                        ZugHandler.log(Level.WARNING, "Worker interrupted: " + getName());
                    }
                    running = false;
                }
            }
            ZugHandler.log(Level.INFO, "Worker stopped: " + getName());
        }
    }

    public void startCleaner(long cleanFreq) {
        new WorkerProc(cleanFreq, this::cleanup).start();
    }

    /**
     * Clears defunct areas and users.
     * NOTE: assumes ZugHandler's inherited `areas` field is narrowed via typedAreas() —
     * see the comment above that method.
     */
    public synchronized void cleanup() {
        areas.values().stream().filter(Timeoutable::timedOut).forEach(area -> {
            area.spam(ZugServMsgType.servMsg, "Closing " + area.getDesc() + " (reason: timeout)");
            areaClosed(area);
        });
        users.values().stream().filter(user -> user.timedOut() && areasByUserToJSON(user).isEmpty()).forEach(user -> {
            log("Removing (idle): " + user.getUniqueName());
            user.getConn().close("User Disconnection/Idle");
            users.remove(user.getUniqueName().toString());
        });
    }

    public void startPings(long pingFreq) {
        new WorkerProc(pingFreq, this::pingAll).start();
    }

    public synchronized void pingAll() {
        serv.getAllConnections(true).forEach(conn -> conn.tell(ZugServMsgType.ping,""));
    }

    private final MessageManager messageManager = new MessageManager();
    private final SeekManager seekManager = new SeekManager(this);

    private boolean requirePassword = true;
    private boolean allowGuests = true;
    private boolean swapGuestConnection = false;
    private boolean fancyGuestNames = true;
    private final List<Class<? extends Enum<?>>> commandList = new ArrayList<>();
    private int crowdThreshold = 100;
    private final Map<MonthDay,Set<String>> trafficMap = new HashMap<>();
    private static final AtomicLong idCounter = new AtomicLong();
    public static String createID() {
        return String.valueOf(idCounter.getAndIncrement());
    }

    @FunctionalInterface
    public interface CommandHandler {
        void handleCommand(ZugUser user,JsonNode data);
    }

    private final Map<Enum<?>,CommandHandler> handMap = new HashMap<>();

    public ZugManager(ZugServ.ServType type, int port) {
        this(type,port, new ArrayList<>(), null);
    }

    public ZugManager(ZugServ.ServType type, int port, List<String> hosts, Map<ZugAuthSource,Boolean> auths) {
        this(type,port, "ws",hosts, auths);
    }

    public ZugManager(ZugServ.ServType type, int port, String ep, List<String> hosts, Map<ZugAuthSource,Boolean> auths) {
        super(type,port, ep, hosts, auths);
        addMessageList(ZugClientMsgType.class);
        addHandler(ZugClientMsgType.newArea,this::handleCreateArea);
        addHandler(ZugClientMsgType.joinArea,this::handleJoinArea);
        addHandler(ZugClientMsgType.partArea,this::handlePartArea);
        addHandler(ZugClientMsgType.startArea,this::handleStartArea);
        addHandler(ZugClientMsgType.seek,this::handleSeek);

        addHandler(ZugClientMsgType.servMsg,this::handleServerMessage);
        addHandler(ZugClientMsgType.privMsg,this::handlePrivateMessage);
        addHandler(ZugClientMsgType.areaMsg,this::handleAreaMsg);

        addHandler(ZugClientMsgType.updateServ,this::handleUpdateServ);
        addHandler(ZugClientMsgType.updateArea,this::handleUpdateArea);
        addHandler(ZugClientMsgType.updateOccupant,this::handleUpdateOccupant);
        addHandler(ZugClientMsgType.updateUser,this::handleUpdateUser);
        addHandler(ZugClientMsgType.getMessages,this::handleUpdateMessages);

        addHandler(ZugClientMsgType.setDeaf,this::handleDeafen);
        addHandler(ZugClientMsgType.ban,this::handleBan);
        addHandler(ZugClientMsgType.kick,this::handleKick);
        addHandler(ZugClientMsgType.nudge,this::handleNudge);
        addHandler(ZugClientMsgType.response,this::handleResponse);
        addHandler(ZugClientMsgType.getOptions,this::handleUpdateOptions);
        addHandler(ZugClientMsgType.setOptions,this::handleSetOptions);
    }

    public void start() {
        serv.startSrv();
        startPings(20000);
        startCleaner(999999);
    }

    public boolean requiringPassword() { return requirePassword; }
    public boolean swappingGuestConnection() { return swapGuestConnection; }
    public void setSwapGuestConnection(boolean swapGuestConnection) { this.swapGuestConnection = swapGuestConnection; }
    public boolean usingFancyGuestNames() { return fancyGuestNames; }
    public void setFancyGuestNames(boolean fancyGuestNames) { this.fancyGuestNames = fancyGuestNames; }
    public int getCrowdThreshold() { return crowdThreshold; }
    public void setCrowdThreshold(int n) { crowdThreshold = n; }
    public void addMessageList(Class<? extends Enum<?>> e) { commandList.add(e); }
    public void addHandler(Enum<?> e, CommandHandler handler) { handMap.put(e,handler); }

    @Override
    public void handleMsg(Connection conn, String type, JsonNode dataNode) {
        ZugUser user = getUserByConn(conn).orElse(null);
        if (user != null) user.action(Timeoutable.ActionType.user);

        if (!getRateLimitManager().allow(conn, user)) {
            log(Level.FINE, "Rate limit exceeded for: " + (user != null ? user.getName() : conn.getAddress()));
            tell(conn, ZugServMsgType.alertMsg, "Rate limit exceeded. Please slow down.");
            return;
        }

        log(Level.FINE,"New Message from " + (user == null ? "?" : user.getName()) + ": " + type + "," + dataNode);
        if (equalsType(type, ZugClientMsgType.login)) {
            if (user != null) err(conn,"Already logged in");
            else handleLoginRequest(conn,dataNode);
        } else if (equalsType(type, ZugClientMsgType.ip)) {
            getTxtNode(dataNode, ZugFields.ADDRESS).ifPresentOrElse(
                    addressStr -> {
                        boolean success = conn.setClientReportedAddress(addressStr);
                        if (success) {
                            ZugHandler.log(Level.FINE, "Client address set for connection " + conn.getID() + ": " + addressStr);
                            conn.lockAddress();
                        } else {
                            ZugHandler.log(Level.WARNING, "Invalid client address for connection " + conn.getID() + ": " + addressStr);
                        }
                        tell(conn, ZugServMsgType.ip, ZugUtils.newJSON().put(ZugFields.ADDRESS, conn.getAddress()));
                    },
                    () -> tell(conn, ZugServMsgType.ip, ZugUtils.newJSON().put(ZugFields.ADDRESS, conn.getAddress()))
            );
        } else if (equalsType(type, ZugClientMsgType.obs)) {
            log(Level.FINE,"Obs requested from: " + conn.getID());
            getArea(dataNode).ifPresent(area -> area.addObserver(conn));
        } else if (equalsType(type, ZugClientMsgType.unObs)) {
            log(Level.FINE,"UnObs requested from: " + conn.getID());
            getArea(dataNode).ifPresent(area -> area.removeObserver(conn));
        } else if (user != null) handleUserMsg(user,type,dataNode);
        else handleUnsupportedMsg(conn,type,dataNode,null);
    }

    public void handleUserMsg(ZugUser user, String type, JsonNode dataNode) {
        List<CommandHandler> handleList = new ArrayList<>();
        commandList.forEach(cmdSet -> {
            try {
                Arrays.stream(cmdSet.getEnumConstants())
                        .filter(eCon -> eCon.name().equalsIgnoreCase(type))
                        .forEach(e -> {
                            CommandHandler handler = handMap.get(e);
                            if (handler != null) {
                                handler.handleCommand(user,dataNode);
                                handleList.add(handler);
                            }
                        });
            } catch (IllegalArgumentException ignore) {}
        });
        if (handleList.isEmpty()) {
            handleUnsupportedMsg(user.getConn(),type,dataNode,user);
        }
    }

    /**
     * Resolves the area and occupant for a message and invokes action if both are found and correctly typed.
     * Simplified now that T/A are fixed by the ZugManager subclass — no runtime Class checks needed.
     */
    protected void withOccupant(ZugUser user, JsonNode dataNode, BiConsumer<A, O> action) {
        getArea(dataNode).ifPresentOrElse(area ->
                getOccupant(user, dataNode).ifPresentOrElse(
                        occ -> action.accept(area, occ),
                        () -> err(user, ERR_NOT_OCCUPANT)
                ), () -> err(user, ERR_AREA_NOT_FOUND));
    }

    /* *** */

    public void handleServerMessage(ZugUser user, JsonNode dataNode) {
        JsonNode msg = ZugUtils.newJSON().set(ZugFields.ZUG_MSG,
                new ZugMessage(new ZugMessage.ZugText(dataNode.get(ZugFields.ZUG_TEXT)), user).toJSON());
        spam(ZugServMsgType.servUserMsg, msg);
        messageManager.addMessage(msg);
    }

    public void handlePrivateMessage(ZugUser user, JsonNode dataNode) {
        getUniqueName(dataNode).ifPresentOrElse(uName -> sendPrivateMsg(user,uName,
                        getTxtNode(dataNode,ZugFields.MSG).orElse("")),
                () -> err(user,"Missing user name"));
    }

    public Optional<A> handleCreateArea(List<ZugUser> users, JsonNode dataNode, boolean fill) {
        Optional<A> optArea = handleCreateArea(users.get(0),dataNode);
        optArea.ifPresent(area -> {
            if (fill) {
                area.setMaxOccupants(users.size());
            }
            for (ZugUser user : users) createOccupantAndJoin(area,user,dataNode);
        });
        return optArea;
    }

    public Optional<A> handleCreateArea(ZugUser user, JsonNode dataNode) {
        String title = getTxtNode(dataNode, ZugFields.AREA_TITLE, true)
                .map(rawTitle -> {
                    try {
                        String validated = InputValidator.validateAreaTitle(rawTitle);
                        log(Level.FINE, "Area title validated: " + validated);
                        return validated;
                    } catch (IllegalArgumentException e) {
                        log(Level.WARNING, "Area title validation failed for user " + user.getName() +
                                ": '" + rawTitle + "' - " + e.getMessage());
                        err(user, "Invalid area title: " + e.getMessage());
                        return null;
                    }
                })
                .orElseGet(() -> {
                    log(Level.FINE, "No title provided, generating...");
                    return generateAreaName();
                });

        if (title == null) {
            return Optional.empty();
        }

        if (getArea(dataNode).isPresent()) {
            err(user, "Already exists: " + title);
            return Optional.empty();
        }

        Optional<A> a = handleCreateArea(user, title, dataNode);
        user.tell("Creating: " + title);
        log("Creating: " + title);
        a.ifPresentOrElse(area -> handleAreaCreated(area,dataNode),
                () -> err(user,"Failed to create area: " + title));
        return a;
    }

    public String generateAreaName() {
        String name = new Faker().chess().opening().replace(" ","").replace("'","");
        if (getAreaByTitle(name).isPresent()) return name + createID(); else return name;
    }

    public Optional<A> handleJoinArea(ZugUser user, JsonNode dataNode) {
        if (getTxtNode(dataNode, ZugFields.AREA_ID).isEmpty()) {
            return handleJoinRandomArea(user,dataNode);
        }
        Optional<A> a = getArea(dataNode);
        a.ifPresentOrElse(zugArea -> createOccupantAndJoin(zugArea, user, dataNode), () -> err(user, ERR_AREA_NOT_FOUND));
        return a;
    }

    public Optional<A> handleJoinRandomArea(ZugUser user, JsonNode dataNode) {
        AtomicReference<Optional<A>> a = new AtomicReference<>(areas.values().stream()
                .filter(ZugArea::isOpen).sorted().findFirst());
        a.get().ifPresentOrElse(area -> handleCreateOccupant(user, area, dataNode)
                        .ifPresent(occupant -> joinArea(area,occupant))
                , () -> a.set(handleCreateArea(user, dataNode)));
        return a.get();
    }

    public Optional<A> handlePartArea(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                        .ifPresentOrElse(occupant -> {
                            if (canPartArea(zugArea, occupant, dataNode)) {
                                if (zugArea.dropOccupant(occupant)) {
                                    log(Level.FINE,"Dropping occupant " + occupant);
                                }
                            } else {
                                occupant.setAway(true);
                            }
                        }, () -> err(user, ERR_NOT_OCCUPANT)),
                () -> err(user, ERR_TITLE_NOT_FOUND));
        return a;
    }

    public Optional<A> handleStartArea(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresentOrElse(area -> startArea(area,user,dataNode), () -> err(user,"Area not found"));
        return a;
    }

    public void handleSeek(ZugUser user, JsonNode dataNode) {
        createSeek(user,dataNode).ifPresent(seekManager::addSeek);
        if (seekManager.seekMap.containsKey(user)) user.tell("Seek created...");
    }

    public Optional<ZugSeek> createSeek(ZugUser user, JsonNode dataNode) {
        return Optional.of(new ZugSeek(user));
    }

    public void startArea(A area, ZugUser user, JsonNode dataNode) {
        area.startArea(user,dataNode)
                .thenAccept(starting -> { if (starting) areaStarted(area); });
    }

    public Optional<A> handleAreaMsg(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresentOrElse(zugArea -> zugArea.getOccupant(user)
                        .ifPresentOrElse(occupant -> {
                            JsonNode textNode = dataNode.get(ZugFields.ZUG_TEXT);
                            if (textNode == null) {
                                err(user, "Missing message content");
                                return;
                            }
                            try {
                                InputValidator.validateZugText(textNode);
                            } catch (IllegalArgumentException e) {
                                err(user, "Invalid message format: " + e.getMessage());
                                return;
                            }
                            ZugMessage.ZugText zugTxt = new ZugMessage.ZugText(textNode);
                            if (zugTxt.getLength() > maxMsgLen) {
                                zugArea.err(user, "Area message overflow!");
                                return;
                            }
                            sendAreaChat(occupant, zugTxt, zugArea);
                        }, () -> err(user, ERR_NOT_OCCUPANT)),
                () -> err(user, ERR_TITLE_NOT_FOUND));
        return a;
    }

    public void handleUpdateServ(ZugUser user, JsonNode dataNode) {
        updateServ(user.getConn());
    }

    public Optional<A> handleUpdateArea(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresent(area -> {
            if (!area.isPrivate()) user.tell(ZugServMsgType.updateArea,area.toJSON2(ZugScope.all));
            else getOccupant(user,dataNode).ifPresent(occupant ->
                    area.tell(occupant, ZugServMsgType.updateArea, area.toJSON2(ZugScope.all)));
        });
        return a;
    }

    public Optional<A> handleUpdateOccupant(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresentOrElse(area -> area.getOccupant(user)
                        .ifPresentOrElse(occupant -> user.getConn().tell(ZugServMsgType.updateOccupant,occupant.toJSON()),
                                () -> err(user.getConn(), ERR_OCCUPANT_NOT_FOUND)),
                () -> err(user.getConn(), ERR_AREA_NOT_FOUND));
        return a;
    }

    public void handleUpdateUser(ZugUser user, JsonNode dataNode) {
        getTxtNode(dataNode, ZugFields.NAME)
                .ifPresentOrElse(name -> getUserByName(name,getTxtNode(dataNode, ZugFields.SOURCE).orElse(null))
                                .ifPresentOrElse(usr -> user.update(user.getConn()),
                                        () -> err(user.getConn(), ERR_USER_NOT_FOUND)),
                        () -> user.update(user.getConn()));
    }

    public Optional<A> handleUpdateMessages(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresent(area -> user.tell(ZugServMsgType.msgHistory,area.toJSON2(ZugScope.msg_history)));
        return a;
    }

    public void handleDeafen(ZugUser user, JsonNode dataNode) {
        getOccupant(user,dataNode).ifPresent(occupant -> getBoolNode(dataNode,ZugFields.DEAFENED).ifPresent(occupant::setDeafened));
    }

    public Optional<A> handleBan(ZugUser user, JsonNode dataNode) {
        Optional<A> areaOpt = getArea(dataNode);

        if (areaOpt.isEmpty()) {
            err(user, ERR_AREA_NOT_FOUND);
            return areaOpt;
        }

        A area = areaOpt.get();

        Optional<O> occupantOpt = getOccupant(user, dataNode);
        if (occupantOpt.isEmpty()) {
            err(user, "You are not in that area");
            return areaOpt;
        }

        Optional<ZugUser.UniqueName> nameOpt = getUniqueName(dataNode.get(ZugFields.NAME));
        if (nameOpt.isEmpty()) {
            err(user, "Invalid target username");
            return areaOpt;
        }

        try {
            area.banOccupant(user, nameOpt.get(), 15 * 60 * 1000, true);
            log(Level.FINE, "Banned user: " + nameOpt.get());
        } catch (Exception e) {
            log(Level.SEVERE, "Ban failed for " + user.getName() + ": " + e.getMessage());
            err(user, "Ban operation failed");
        }

        return areaOpt;
    }

    public Optional<A> handleKick(ZugUser kicker, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresent(area ->
                getJSONNode(dataNode, ZugFields.UNAME)
                        .flatMap(uName -> getUserByUniqueName(new ZugUser.UniqueName(uName)))
                        .flatMap(user -> getOccupant(user, dataNode))
                        .ifPresent(occupant -> area.kick(occupant, kicker)));
        return a;
    }

    public Optional<A> handleNudge(ZugUser nudger, JsonNode dataNode) {
        Optional<O> occupant = getOccupant(nudger, dataNode);
        occupant.ifPresent(o -> o.getArea().nudgeArea(o));
        return occupant.map(o -> (A) o.getArea());
    }

    public Optional<A> handleResponse(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);

        String responseType = getTxtNode(dataNode, ZugFields.RESPONSE_TYPE).orElse(null);
        if (!InputValidator.isValidResponseType(responseType)) {
            err(user, "Invalid response type");
            return a;
        }

        if (!dataNode.has(ZugFields.RESPONSE)) {
            err(user, "Missing response value");
            return a;
        }

        Object response;
        JsonNode responseNode = dataNode.get(ZugFields.RESPONSE);

        if (responseNode.isBoolean()) {
            response = responseNode.asBoolean();
        } else if (responseNode.isInt()) {
            int intVal = responseNode.asInt();
            if (intVal < -1000000 || intVal > 1000000) {
                InputValidator.logValidationFailure("INT_RESPONSE_OOB", Integer.toString(intVal));
                err(user, "Response value out of range");
                return a;
            }
            response = intVal;
        } else if (responseNode.isDouble()) {
            double dblVal = responseNode.asDouble();
            if (Double.isNaN(dblVal) || Double.isInfinite(dblVal)) {
                InputValidator.logValidationFailure("INVALID_DOUBLE", Double.toString(dblVal));
                err(user, "Invalid numeric response");
                return a;
            }
            response = dblVal;
        } else if (responseNode.isTextual()) {
            response = responseNode.asText();
        } else {
            response = null;
            err(user, "Unknown response type");
            return a;
        }

        Optional<O> occupantOpt = a.flatMap(area -> area.getOccupant(user));

        if (occupantOpt.isEmpty()) {
            log(Level.WARNING, "User " + user.getName() + " not in area for response");
            err(user, ERR_NOT_OCCUPANT);
            return a;
        }

        try {
            occupantOpt.get().setResponse(responseType, response);
            log(Level.FINE, "Response set for " + user.getName());
        } catch (Exception e) {
            log(Level.SEVERE, "Error setting response: " + e.getMessage());
            err(user, "Response processing error");
            ZugServ.printStackTrace(e);
        }

        return a;
    }

    public Optional<A> handleUpdateOptions(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresent(area -> area.updateOptions(user));
        return a;
    }

    public Optional<A> handleSetOptions(ZugUser user, JsonNode dataNode) {
        Optional<A> a = getArea(dataNode);
        a.ifPresent(area -> getJSONNode(dataNode,ZugFields.OPTIONS)
                .ifPresent(options -> area.setOptions(user,options)));
        return a;
    }

    public void addArea(A area, boolean autoJoin, JsonNode dataNode) {
        addOrGetArea(area);
        if (autoJoin) {
            area.getCreator().ifPresent(creator -> createOccupantAndJoin(area,creator,dataNode));
        }
        areaCreated(area);
    }

    /* *** */

    private void handleAreaCreated(A area, JsonNode dataNode) {
        Optional<Boolean> join = getBoolNode(dataNode, ZugFields.AUTO_JOIN);
        addArea(area,join.isEmpty() || join.get(),dataNode);
    }

    private void createOccupantAndJoin(A area, ZugUser user, JsonNode dataNode) {
        Optional<O> existingOccupant = area.getOccupant(user);
        existingOccupant.ifPresentOrElse(
                existing -> {
                    area.rejoin(existing);
                    log(Level.FINE, "User rejoining: " + user.getName());
                },
                () -> {
                    if (!area.config.allowGuests && user.isGuest()) {
                        log(Level.INFO, "Guest rejected from area: " + area.getDesc());
                        err(user, "Sorry, guests are not allowed in this area");
                        return;
                    }

                    if (area.numOccupants() >= area.getMaxOccupants()) {
                        log(Level.INFO, "Area full: " + area.getDesc());
                        handleMaxOccupancy(user, area, dataNode);
                        return;
                    }

                    Optional<O> newOccupantOpt = handleCreateOccupant(user, area, dataNode);
                    newOccupantOpt.ifPresentOrElse(
                            occupant -> {
                                joinArea(area, occupant);
                                log(Level.FINE, "User joined: " + user.getName() + " in " + area.getDesc());
                            },
                            () -> {
                                log(Level.WARNING, "Failed to create occupant for " + user.getName());
                                err(user, "Failed to join area");
                            }
                    );
                }
        );
    }

    private void joinArea(A area, O occupant) {
        if (area.addOccupant(occupant)) {
            log(Level.FINE, "Joined " +  area.getDesc() + ": " + occupant);
        }
    }

    public void handleMaxOccupancy(ZugUser user, A area, JsonNode dataNode) {
        if (area.isBumpAway()) {
            area.getOccupants()
                    .filter(o -> o.canAct() && !area.isCreator(o.getUser()))
                    .findFirst().ifPresent(occupant -> {
                        area.spam("Dropping idle occupant: " + occupant.getName());
                        if (area.dropOccupant(occupant)) createOccupantAndJoin(area,user,dataNode);
                    });
        } else {
            err(user,"Game full: " + area.getDesc());
        }
    }

    @Override
    public void areaJoined(A area, O occupant) {
        area.tell(occupant, ZugServMsgType.joinArea,area.toJSON2(ZugScope.all,ZugScope.msg_history));
        areaUpdated(area,"joined");
    }

    @Override
    public void areaParted(A area, ZugUser user) {
        user.tell(ZugServMsgType.partArea,ZugUtils.newJSON().put(ZugFields.AREA_ID,area.getID()));
        areaUpdated(area,"parted");
    }

    /* *** */

    public void updateServ(Connection conn) {
        tell(conn, ZugServMsgType.updateServ, toJSON2(ZugScope.all,ZugScope.msg_history));
    }

    public Optional<O> getOccupant(ZugUser user, JsonNode dataNode) {
        return getArea(dataNode).flatMap(area -> area.getOccupant(user));
    }

    public void sendAreaChat(O occupant, ZugMessage.ZugText zugTxt, A area) {
        if (zugTxt.getLength() > maxMsgLen) {
            occupant.getArea().err(occupant.getUser(), "Area message overflow!");
        } else {
            area.spam(ZugServMsgType.areaUserMsg,ZugUtils.newJSON()
                    .set(ZugFields.ZUG_MSG,new ZugMessage(zugTxt,occupant.getUser()).toJSON()));
        }
    }

    public void sendRoomChat(O occupant, ZugMessage.ZugText zugTxt, ZugRoom<O> room) {
        ObjectNode chatNode = ZugUtils.newJSON().set(ZugFields.OCCUPANT,occupant.toJSON2(ZugScope.basic));
        room.spam(ZugServMsgType.roomUserMsg,chatNode
                .set(ZugFields.ZUG_MSG,new ZugMessage(zugTxt,occupant.getUser()).toJSON()));
    }

    public void sendPrivateMsg(ZugUser user1, ZugUser.UniqueName name, String msg) {
        if (msg.length() > maxMsgLen) {
            err(user1, "Private message overflow!");
        } else {
            getUserByUniqueName(name).ifPresentOrElse(user2 -> {
                user2.tell(ZugServMsgType.privMsg,ZugUtils.newJSON().put(ZugFields.MSG,msg).set(ZugFields.USER,user1.toJSON2(ZugScope.basic)));
                user1.tell(ZugServMsgType.servMsg,"Message sent to " + name + ": " + msg);
            }, () -> err(user1,"User not found: " + name));
        }
    }

    public void handleLoginRequest(Connection conn, JsonNode dataNode) {
        try {
            ZugAuthSource source = ZugAuthSource.valueOf(dataNode.get(ZugFields.LOGIN_TYPE).textValue().toLowerCase());
            if (source == ZugAuthSource.lichess) {
                getTxtNode(dataNode,ZugFields.TOKEN).ifPresentOrElse(
                        token -> {
                            if (token.isEmpty() || token.length() > 1000) {
                                err(conn, "Login failure: Invalid token format");
                                return;
                            }
                            handleLichessLogin(conn, token);
                        },
                        () -> err(conn,"Empty token"));
            } else if (source == ZugAuthSource.google) {
                getTxtNode(dataNode,ZugFields.TOKEN).ifPresentOrElse(
                        token -> {
                            if (token.isEmpty() || token.length() > 2000) {
                                err(conn, "Login failure: Invalid token format");
                                return;
                            }
                            handleGoogleLogin(conn, token);
                        },
                        () -> err(conn,"Empty token"));
            } else if (source == ZugAuthSource.none) {
                if (allowGuests) try {
                    handleLogin(conn, generateGuestName(getTxtNode(dataNode,ZugFields.NAME).orElse(ZugFields.GUEST)), dataNode);
                } catch (Exception e) {
                    ErrorContext.logError("Guest Login", "generateGuestName", String.valueOf(conn.getID()), e);
                    err(conn, "Login processing error. Please try again.");
                } else err(conn,"Login error: guests not allowed");
            } else err(conn,"Login error: source not found");
        } catch (IllegalArgumentException e) {
            err(conn,"Login error: bad source");
        }
    }

    @Override
    public void handleLogin(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode) {
        getUsers().values().stream()
                .filter(user -> user.sameUser(uName, conn))
                .findFirst()
                .ifPresentOrElse(
                        prevUser -> swapConnection(prevUser, conn, dataNode),
                        () -> handleCreateUser(conn, uName, dataNode)
                                .ifPresentOrElse(
                                        newUser -> addOrGetUser(newUser)
                                                .ifPresentOrElse(
                                                        wtf -> err(conn, "Error: duplicate user!"),
                                                        () -> handleLoggedIn(newUser,dataNode)
                                                ),
                                        () -> err(conn, "Login error")
                                )
                );
    }

    public void handleLoggedIn(ZugUser user, JsonNode dataNode) {
        log("logged in: " + user.getUniqueName());
        user.setLoggedIn(true);
        user.tell(ZugServMsgType.logOK,user.toJSON());
        user.tell(ZugServMsgType.areaList, areasByUserToJSON(true,isCrowded() ? user : null));
        updateServ(user.getConn());
        MonthDay monthDay = MonthDay.now();
        trafficMap.putIfAbsent(monthDay,new HashSet<>());
        if (!user.isGuest()) trafficMap.get(monthDay).add(user.getUniqueName().toString());
        getTxtNode(dataNode,ZugFields.AREA_ID).ifPresent(id -> autoJoin(user,id, dataNode));
    }

    public void autoJoin(ZugUser user, String areaId, JsonNode dataNode) {
        getAreaById(areaId).ifPresentOrElse(area -> createOccupantAndJoin(area,user, dataNode),
                () -> err(user, "Error: unknown area"));
    }

    public void swapConnection(ZugUser prevUser, Connection newConn, JsonNode dataNode) {
        newConn.tell(ZugServMsgType.servMsg,"Already logged in, swapping connections");
        prevUser.setConn(newConn);
        handleLoggedIn(prevUser,dataNode);
    }

    public Optional<ZugUser.UniqueName> getUniqueName(JsonNode dataNode) {
        String source = getTxtNode(dataNode,ZugFields.SOURCE)
                .orElse(getTxtNode(dataNode.get(ZugFields.NAME),ZugFields.SOURCE).orElse(""));
        String name = getTxtNode(dataNode,ZugFields.NAME)
                .orElse(getTxtNode(dataNode.get(ZugFields.NAME),ZugFields.NAME).orElse(""));
        try {
            return Optional.of(new ZugUser.UniqueName(name, ZugAuthSource.valueOf(source)));
        } catch (IllegalArgumentException arg) {
            return Optional.empty();
        }
    }

    public abstract Optional<ZugUser> handleCreateUser(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode);
    public abstract Optional<A> handleCreateArea(ZugUser user, String title, JsonNode dataNode);
    public abstract Optional<O> handleCreateOccupant(ZugUser user, A area, JsonNode dataNode);

    public boolean canPartArea(A area, O occupant, JsonNode dataNode) {
        return true;
    }

    public abstract void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user);

    public void handleAreaListUpdate(A area, ZugAreaChange change, String updateType) {
        if (!isCrowded() && area.exists() && !area.isPrivate()) {
            spam(ZugServMsgType.updateAreaList,ZugUtils.newJSON()
                    .put(ZugFields.AREA_CHANGE,change.name())
                    .put(ZugFields.UPDATE_TYPE,updateType)
                    .set(ZugFields.AREA,area.toJSON2(ZugScope.basic,ZugScope.occupants_basic)));
        }
    }

    @Override
    public void areaCreated(A area) {
        area.getCreator().ifPresent(creator -> creator.tell(ZugServMsgType.createArea, area.toJSON2(ZugScope.all)));
        handleAreaListUpdate(area, ZugAreaChange.created,"creating");
        area.created = true;
    }

    @Override
    public void areaClosed(A area) {
        handleAreaListUpdate(area, ZugAreaChange.deleted,"deleting");
        area.setExistence(false);
        removeArea(area);
    }

    @Override
    public void areaStarted(A area) {
        log(Level.FINE, "area started: " + area);
        areaUpdated(area,"started");
    }

    @Override
    public void areaFinished(A area) {
        log(Level.FINE, "area finished: " + area);
        areaUpdated(area,"finished");
    }

    @Override
    public void areaUpdated(A area, String updateType) {
        handleAreaListUpdate(area, ZugAreaChange.updated,updateType);
    }

    public boolean requiresPassword() { return requirePassword; }
    public void setRequirePassword(boolean bool) { requirePassword = bool; }
    public boolean allowingGuests() { return allowGuests; }
    public void setAllowGuests(boolean bool) { allowGuests = bool; }
    public boolean isCrowded() { return users.size() > crowdThreshold; }

    @Override
    public void connected(Connection conn) {
        if (getActiveConnectionCount() >= getServ().getMaxConnections()) {
            log(Level.WARNING, "Connection rejected: limit reached (" + getServ().getMaxConnections() +
                    " active). From: " + conn.getAddress());
            tell(conn, ZugServMsgType.errMsg, "Server is at capacity. Try again later.");
            conn.close("Server at capacity");
            return;
        }
        log(Level.FINE, "Connection accepted. Active: " + getActiveConnectionCount() + "/" + getServ().getMaxConnections());
        log(Level.INFO,"Connection accepted. ID: " + conn.getID());
        tell(conn, ZugServMsgType.reqLogin,ZugUtils.newJSON().put(ZugFields.USER_ID,conn.getID()));
    }

    @Override
    public void err(Connection conn, String msg) { tell(conn, ZugServMsgType.errServMsg, msg); }

    @Override
    public void msg(Connection conn, String msg) { tell(conn, ZugServMsgType.servMsg, msg); }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        ObjectNode node = ZugUtils.newJSON();
        if (isBasic(scopes)) {
            Set<String> dailyUsers = trafficMap.get(MonthDay.now());
            node.put(ZugFields.CROWDED, isCrowded())
                    .put(ZugFields.ALLOW_GUESTS, allowGuests)
                    .put(ZugFields.USERS, getUsers().size())
                    .put(ZugFields.LOGGED_IN, getUsers().values().stream().filter(ZugUser::isLoggedIn).count())
                    .put(ZugFields.DAILY_USERS, dailyUsers != null ? dailyUsers.size() : 0);
        }
        if (hasScope(ZugScope.msg_history,true,scopes)) {
            node.set(ZugFields.MSG_HISTORY,messageManager.toJSONArray());
        }
        return node;
    }

    /**
     * Looks for and returns the area (as this manager's concrete type A) with the id specified in dataNode.
     */
    public Optional<A> getArea(JsonNode dataNode) {
        return getTxtNode(dataNode, ZugFields.AREA_ID).flatMap(this::getAreaById);
    }

    public ZugUser.UniqueName generateGuestName(String name) {
        if (fancyGuestNames && name.equals(ZugFields.GUEST)) {
            name = new Faker().artist().name().replace(" ","") + new Faker().animal().name();
        }
        final StringBuilder userName = new StringBuilder(name);
        int i = 0;
        while (users.values().stream().anyMatch(user -> user.getName().equalsIgnoreCase(userName.toString()))) {
            userName.replace(0,userName.length(),name + (++i));
        }
        return new ZugUser.UniqueName(userName.toString(), ZugAuthSource.none);
    }

}