package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.datafaker.Faker;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.chernovia.lib.zugserv.ZugHandler.log;

enum ZugAreaPhase {initializing,confirming,running,finalizing}

/**
 * ZugArea is a fuller featured extension of ZugRoom that includes passwords, bans, options, phases, and observers.
 */
abstract public class ZugArea extends ZugRoom implements OccupantListener,Runnable {

    public enum OperationType {start,stop}

    final private AreaListener listener;
    private boolean purgeDeserted = true;
    private boolean purgeAway = true;
    private String password;
    private ZugUser creator;
    private final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    private final List<Ban> banList = new ArrayList<>();
    private boolean exists = true;
    private Enum<?> phase = ZugAreaPhase.initializing;
    private long phaseStamp = 0;
    private long phaseTime = 0;
    private Thread areaThread;
    boolean running = false;
    public ZugOptions om = new ZugOptions();

    public record ObjResponse (Optional<Object> response, Occupant occupant) {}
    public record BoolResponse (Optional<Boolean> response, Occupant occupant) {}
    public record IntResponse (Optional<Integer> response, Occupant occupant) {}
    public record DoubleResponse (Optional<Double> response, Occupant occupant) {}
    public record StringResponse (Optional<String> response, Occupant occupant) {}

    private final Map<String,CompletableFuture<List<ObjResponse>>> objCheckerMap = new HashMap<>();
    private final Map<String,CompletableFuture<List<BoolResponse>>> boolCheckerMap = new HashMap<>();
    private final Map<String,CompletableFuture<List<IntResponse>>> intCheckerMap = new HashMap<>();
    private final Map<String,CompletableFuture<List<DoubleResponse>>> doubleCheckerMap = new HashMap<>();
    private final Map<String,CompletableFuture<List<StringResponse>>> stringCheckerMap = new HashMap<>();


    /**
     * Constructs a ZugArea with a title, creator, and AreaListener.
     * @param t the title
     * @param c the creator
     * @param l an AreaListener
     */
    public ZugArea(String t, ZugUser c, AreaListener l) {
        this(t,ZugFields.UNKNOWN_STRING,c, l);
    }

    /**
     * Constructs a ZugArea with a title, password, creator, and AreaListener.
     * @param t the title
     * @param p the password
     * @param c the creator
     * @param l an AreaListener
     */
    public ZugArea(String t, String p, ZugUser c, AreaListener l) { //l.areaCreated(this);
        super(t);
        password = p; creator = c; listener = l;
        areaThread = new Thread(this);
        action();
    }

    /**
     * Sets the existance of the ZugArea (i.e., if it's any longer being used)
     * @param e true for existence
     */
    public void setExistence(boolean e) {
        exists = e;
    }

    /**
     * Indicates if the ZugArea "exists" (i.e., if it's any longer being used)
     * @return true for existence
     */
    public boolean exists() {
        return exists;
    }

    /**
     * Gets the AreaListener for the area (typically a species of ZugHandler or ZugManager).
     * @return the area's AreaListener
     */
    public AreaListener getListener() {
        return listener;
    }

    public Optional<ZugUser> getCreator() {
        return Optional.of(creator);
    }

    public boolean isCreator(ZugUser user) {
        return Objects.equals(user,creator);
    }

    public void setCreator(ZugUser creator) {
        this.creator = creator;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean okPass(String pwd) {
        return (password.equals(ZugFields.UNKNOWN_STRING) || pwd.equals(password));
    }

    @Override
    public void handleAway(Occupant occupant) {
        if (isDeserted(purgeAway) && purgeDeserted) stopArea(true);
    }

    @Override
    public void handleRoomJoin(Occupant occupant, ZugRoom prevRoom, ZugRoom newRoom) {}

    public void setPurgeAway(boolean purgeAway) {
        this.purgeAway = purgeAway;
    }

    public void setPurgeDeserted(boolean purgeDeserted) {
        this.purgeDeserted = purgeDeserted;
    }

    /**
     * Indicates if any bans on the user are currently in effect.
     * @param user the user
     * @return true if banned
     */
    public boolean isBanned(ZugUser user) {
        return banList.stream().anyMatch(ban -> ban.inEffect(user));
    }

    /**
     * Adds an observer by Connection.
     * @param conn the observing Connection
     * @return false if Connection is null or already represents an Occupant
     */
    public boolean addObserver(Connection conn) {  //(isOccupant(conn,true))
        if (conn == null || isOccupant(conn,false)) return false; else action();
        conn.tell(ZugServMsgType.obs,ZugUtils.newJSON().put(ZugFields.AREA_ID,getTitle()));
        return observers.add(conn);
    }

    /**
     * Removes an observing Connection.
     * @param conn the Connection
     * @return true if successful
     */
    public boolean removeObserver(Connection conn) {
        if (conn != null) conn.tell(ZugServMsgType.unObs,ZugUtils.newJSON().put(ZugFields.AREA_ID,getTitle()));
        return observers.remove(conn);
    }

    /**
     * Indicates if a given Connection is currently observing the area.
     * @param conn a Connection
     * @return true if observing
     */
    public boolean isObserver(Connection conn) {
        return observers.contains(conn);
    }

    /**
     * Bans an Occupant (by UniqueName).
     * @param bannor The user (typically the creator of the area) executing the ban
     * @param uniqueName the banned user's uniqueName (to prevent rejoining attempts)
     * @param t the duration of the ban (in milliseconds)
     * @param drop if true, the user is dropped from the Occupant list
     */
    public void banOccupant(ZugUser bannor, ZugUser.UniqueName uniqueName, long t, boolean drop) {
        Occupant occupant = getOccupant(uniqueName).orElse(null);
        if (occupant == null) {
            err(bannor, "Not found: " + uniqueName.name);
        }
        else banOccupant(bannor,occupant,t,drop);
    }

    /**
     * Bans an Occupant.
     * @param bannor The user (typically the creator of the area) executing the ban
     * @param occupant the banned Occupant
     * @param t the duration of the ban (in milliseconds)
     * @param drop if true, the user is dropped from the Occupant list
     */
    public void banOccupant(ZugUser bannor, Occupant occupant, long t, boolean drop) {
        if (bannor.equals(getCreator().orElse(null))) {
            banList.add(new Ban(occupant.getUser(),t,bannor));
            if (drop) dropOccupant(occupant);
            spam(occupant.getUser().getName() + " has been banned");
        }
        else {
            err(bannor,"Only this area's creator can ban");
        }
    }

    /**
     * Sets all options as JSON-formatted data.
     * @param user the user attempting to set the options
     * @param node the JSON-formatted data
     * @return true upon success
     */
    public boolean setOptions(ZugUser user, JsonNode node) { //log("Setting Options: " + node.toString());
        if (user.equals(creator)) try {
            om = new ZugOptions(node); return true;
        } catch (Exception e) { err(user,"Error setting options: " + e.getMessage() + ", json: " + node); }
        else err(user, "Permission denied(not creator)");
        return false;
    }

    /**
     * Update a user of a (presumably changed) Option.
     * @param user the ZugUser to update
     */
    public void updateOptions(ZugUser user) {
        user.tell(ZugServMsgType.updateOptions,ZugUtils.newJSON().put(ZugFields.AREA_ID,getTitle()).set(ZugFields.OPTIONS, om.toJSON()));
    }

    /**
     * Update all Occupants of a (presumably changed) Option.
     */
    public void spamOptions() {
        spam(ZugServMsgType.updateOptions,ZugUtils.newJSON().set(ZugFields.OPTIONS, om.toJSON()));
    }

    /**
     * Sets the current phase.
     * @param p current phase
     * @param quietly suppress client notification
     */
    public void setPhase(Enum<?> p, boolean quietly) {
        action();
        phase = p;
        if (!quietly) spam(ZugServMsgType.phase,phaseToJSON()); //getListener().areaUpdated(this);
    }

    /**
     * Sets a new phase and sleeps for the specified number of seconds or until interrupted.
     * @param p phase to set
     * @param seconds seconds to sleep
     * @return seconds slept
     */
    public boolean newPhase(Enum<?> p, int seconds) {
        phaseTime = seconds * 1000L;
        phaseStamp = System.currentTimeMillis();
        setPhase(p,false);
        boolean timeout = true;
        if (seconds > 0) {
            try { Thread.sleep(phaseTime); } catch (InterruptedException e) { timeout = false; }
        }
        return timeout;
    }

    long getPhaseTimeRemaining() {
        return phaseTime - (System.currentTimeMillis() - getPhaseStamp());
    }

    public ObjectNode phaseToJSON() {
        return ZugUtils.newJSON()
                .put(ZugFields.PHASE,phase.name())
                .put(ZugFields.PHASE_STAMP,getPhaseStamp())
                .put(ZugFields.PHASE_TIME_REMAINING,getPhaseTimeRemaining());
    }

    public Enum<?> getPhase() {
        return phase;
    }

    public boolean isPhase(Enum<?> p) {
        return phase == p;
    }

    public void interruptPhase() {
        if (areaThread != null && areaThread.getState() == Thread.State.TIMED_WAITING) areaThread.interrupt();
    }

    public void setPhaseStamp(long t) { phaseStamp = t; }
    public long getPhaseStamp() { return phaseStamp; }
    public Thread getAreaThread() { return areaThread; }
    public void setAreaThread(Thread areaThread) { this.areaThread = areaThread; }
    public boolean isRunning() { return running; }
    public boolean isOpen() {
        return areaThread == null;
    }

    public boolean isDeserted(boolean countAway) {
        return getActiveOccupants(countAway).isEmpty();
    }

    @Override
    public boolean dropOccupant(ZugUser user) {
        if (super.dropOccupant(user)) { //log("Dropping: " + user.getUniqueName().toString());
            if (isDeserted(purgeAway)) stopArea(true);
            return true;
        }
        return false;
    }

    public void setRunning(boolean running) { this.running = running; }

    public void checkObjResponse(String confType) {
        CompletableFuture<List<ObjResponse>> objFuture = objCheckerMap.get(confType);
        List<ObjResponse> responseMap = getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new ObjResponse(occupant.getObjResponse(confType),occupant)).toList();
        if (responseMap.stream().allMatch(objResponse -> objResponse.response().isPresent())) {
            objFuture.complete(responseMap);
        }
    }

    public void checkBoolResponse(String confType) {
        CompletableFuture<List<BoolResponse>> boolFuture = boolCheckerMap.get(confType);
        List<BoolResponse> responseMap = getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new BoolResponse(occupant.getBoolResponse(confType),occupant)).toList();
        if (responseMap.stream().allMatch(boolResponse -> boolResponse.response().isPresent())) {
            boolFuture.complete(responseMap);
        }
    }

    public void checkIntResponse(String confType) {
        CompletableFuture<List<IntResponse>> intFuture = intCheckerMap.get(confType);
        List<IntResponse> responseMap = getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new IntResponse(occupant.getIntResponse(confType),occupant)).toList();
        if (responseMap.stream().allMatch(intResponse -> intResponse.response().isPresent())) {
            intFuture.complete(responseMap);
        }
    }

    public void checkDoubleResponse(String confType) {
        CompletableFuture<List<DoubleResponse>> dblFuture = doubleCheckerMap.get(confType);
        List<DoubleResponse> responseMap = getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new DoubleResponse(occupant.getDoubleResponse(confType),occupant)).toList();
        if (responseMap.stream().allMatch(dblResponse -> dblResponse.response().isPresent())) {
            dblFuture.complete(responseMap);
        }
    }

    public void checkStringResponse(String confType) {
        CompletableFuture<List<StringResponse>> strFuture = stringCheckerMap.get(confType);
        List<StringResponse> responseMap = getOccupants().stream()
                .filter(occupant -> !occupant.isBot())
                .map(occupant -> new StringResponse(occupant.getStringResponse(confType),occupant)).toList();
        if (responseMap.stream().allMatch(strResponse -> strResponse.response().isPresent())) {
            strFuture.complete(responseMap);
        }
    }

    public CompletableFuture<List<BoolResponse>> requestBoolean(String confType, int timeout) {
        getOccupants().forEach(occupant -> occupant.setBoolResponse(confType,null));
        CompletableFuture<List<BoolResponse>> future = new CompletableFuture<>();
        boolCheckerMap.put(confType, future);
        spam(ZugServMsgType.reqConfirm, ZugUtils.newJSON().put(ZugFields.CONFIRM_TYPE,confType));
        return future.completeOnTimeout(
                getOccupants().stream().filter(occupant -> !occupant.isBot())
                        .map(occupant -> new BoolResponse(occupant.getBoolResponse(confType),occupant))
                        .toList()
                ,timeout, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<IntResponse>> requestInteger(String confType, int timeout) {
        getOccupants().forEach(occupant -> occupant.setIntResponse(confType, null));
        CompletableFuture<List<IntResponse>> future = new CompletableFuture<>();
        intCheckerMap.put(confType, future);
        spam(ZugServMsgType.reqInt, ZugUtils.newJSON().put(ZugFields.CONFIRM_TYPE,confType));
        return future.completeOnTimeout(
                getOccupants().stream().filter(occupant -> !occupant.isBot())
                        .map(occupant -> new IntResponse(occupant.getIntResponse(confType),occupant))
                        .toList()
                ,timeout, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<DoubleResponse>> requestDouble(String confType, int timeout) {
        getOccupants().forEach(occupant -> occupant.setDoubleResponse(confType, null));
        CompletableFuture<List<DoubleResponse>> future = new CompletableFuture<>();
        doubleCheckerMap.put(confType, future);
        spam(ZugServMsgType.reqDbl, ZugUtils.newJSON().put(ZugFields.CONFIRM_TYPE,confType));
        return future.completeOnTimeout(
                getOccupants().stream().filter(occupant -> !occupant.isBot())
                        .map(occupant -> new DoubleResponse(occupant.getDoubleResponse(confType),occupant))
                        .toList()
                ,timeout, TimeUnit.SECONDS);
    }

    public CompletableFuture<List<StringResponse>> requestString(String confType, int timeout) {
        getOccupants().forEach(occupant -> occupant.setStringResponse(confType, null));
        CompletableFuture<List<StringResponse>> future = new CompletableFuture<>();
        stringCheckerMap.put(confType, future);
        spam(ZugServMsgType.reqStr, ZugUtils.newJSON().put(ZugFields.CONFIRM_TYPE,confType));
        return future.completeOnTimeout(
                getOccupants().stream().filter(occupant -> !occupant.isBot())
                        .map(occupant -> new StringResponse(occupant.getStringResponse(confType),occupant))
                        .toList()
                ,timeout, TimeUnit.SECONDS);
    }


    /**
     * Starts an area.  Note this does not send a ZugFields.ServMsgType.startArea message to the client and is a CompleteableFuture in case of subclasses
     * wishing to use requestConfirmation() or what not.
     * @param user The user (typicially the creator of the area) starting the area
     * @param initData initialization data (in JSON format)
     * @return true upon success
     */
    public CompletableFuture<Boolean> startArea(ZugUser user, JsonNode initData) { //ZugManager.log("Starting: " + getTitle());
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (!allowed(user,OperationType.start)) {
            err(user,"Permission denied");
        }
        else if (areaThread.getState() == Thread.State.NEW) { //areaThread = new Thread(this);
            running = true; //spam(ZugFields.ServMsgType.startArea,toJSON(true));
            areaThread.start();
            future.complete(true);
        }
        else err(user,"Area already started");
        future.complete(false);
        return future;
    }

    public void stopArea(boolean close) {
        if (areaThread != null) {
            running = false;
            interruptPhase(); //TODO: join thread?
        }
        if (close) getListener().areaClosed(this);
    }

    public boolean allowed(ZugUser user, OperationType t) {
        return switch (t) {
            case start, stop -> isCreator(user);
        };
    }

    @Override
    public void run() {
    }

    @Override
    final public boolean addOccupant(Occupant occupant) {
        if (super.addOccupant(occupant)) observers.remove(occupant.getUser().getConn()); else return false;
        return true;
    }

    @Override
    final public void spamX(Enum<?> t, String msg, Occupant... ignoreList) {
        super.spamX(t,msg,ignoreList);
        for (Connection conn : observers) {
            if (conn.getStatus() == Connection.Status.STATUS_DISCONNECTED) removeObserver(conn);
            else conn.tell(t,ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.AREA_ID,getTitle()));
        }
    }

    @Override
    final public void spamX(Enum<?> t, ObjectNode msgNode, Occupant... ignoreList) {
        super.spamX(t,msgNode,ignoreList);
        for (Connection conn : observers) {
            if (conn.getStatus() == Connection.Status.STATUS_DISCONNECTED) removeObserver(conn);
            else conn.tell(t,msgNode.put(ZugFields.AREA_ID,getTitle()));
        }
    }

    @Override
    final public void msg(ZugUser user, String msg) {
        user.tell(ZugServMsgType.areaMsg,ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.AREA_ID,getTitle()));
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        ObjectNode node = super.toJSON(scopes);
        if (isBasic(scopes)) {
            node.put(ZugFields.PHASE,getPhase().name())
                    .put(ZugFields.PHASE_TIME_REMAINING,getPhaseTimeRemaining())
                    .put(ZugFields.EXISTS,exists)
                    .put(ZugFields.RUNNING,running)
                    .set(ZugFields.CREATOR,creator != null ? creator.getUniqueName().toJSON() : null);
        }
        if (hasScope(scopes, ZugScope.observers)) {
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            observers.forEach(obs -> arrayNode.add(obs.getID()));
            node.set(ZugFields.OBSERVERS,arrayNode);
        }
        if (hasScope(scopes,ZugScope.options)) {
            node.set(ZugFields.OPTIONS, om.toJSON());
        }
        return node;
    }

    public String generateBotName() {
        String colorName = new Faker().color().name().split(" ")[0];
        if (colorName.length() > 1) {
            return colorName.substring(0,1).toUpperCase() + colorName.substring(1) + new Faker().appliance().equipment().split(" ")[0];
        } else return "wtf:" + colorName;
    }


}
