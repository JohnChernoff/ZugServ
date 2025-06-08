package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.datafaker.Faker;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.*;
import java.util.concurrent.*;

enum ZugAreaPhase {initializing,running,finalizing}

/**
 * ZugArea is a fuller featured extension of ZugRoom that includes passwords, bans, options, phases, and observers.
 */
abstract public class ZugArea extends ZugRoom implements OccupantListener,Runnable {
    public static class AreaConfig {
        public boolean allowGuests;
        public boolean purgeDeserted;
        public boolean purgeAway; //TODO: should this drop away occupants?
        public boolean bumpAway;
        public boolean async;
        public AreaConfig(boolean allowGuests, boolean purgeDeserted, boolean purgeAway, boolean bumpAway, boolean async) {
            this.allowGuests = allowGuests;
            this.purgeDeserted = purgeDeserted;
            this.purgeAway = purgeAway;
            this.bumpAway = bumpAway;
            this.async = async;
        }
    }
    public final AreaConfig config;
    public enum OperationType {start,stop,nudge}
    final private AreaListener listener;
    private String password;
    private ZugUser creator;
    private final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    private final List<Ban> banList = new ArrayList<>();
    private boolean exists = true;
    private Thread areaThread;
    boolean running = false;
    private OptionsManager optionsManager = new OptionsManager();
    private final ResponseManager responseManager;
    private final PhaseManager phaseManager;
    public OptionsManager om() { return optionsManager; }
    public void setOptionsManager(OptionsManager o) {
        optionsManager = o;
    }
    public ResponseManager rm() { return responseManager; }
    public PhaseManager pm() { return phaseManager; }

    /**
     * Constructs a ZugArea with a title, creator, and AreaListener.
     * @param t the title
     * @param c the creator
     * @param l an AreaListener
     */
    public ZugArea(String t, ZugUser c, AreaListener l) {
        this(t,ZugFields.UNKNOWN_STRING,c, l, new AreaConfig(true,true, true, false, true));
    }

    /**
     * Constructs a ZugArea with a title, creator, and AreaListener.
     * @param t the title
     * @param c the creator
     * @param l an AreaListener
     * @param config Area Configuration
     */
    public ZugArea(String t, ZugUser c, AreaListener l, AreaConfig config) {
        this(t,ZugFields.UNKNOWN_STRING,c, l, config);
    }

    /**
     * Constructs a ZugArea with a title, password, creator, and AreaListener.
     * @param t the title
     * @param p the password
     * @param c the creator
     * @param l an AreaListener
     * @param config Area Configuration
     */
    public ZugArea(String t, String p, ZugUser c, AreaListener l, AreaConfig config) { //l.areaCreated(this);
        super(t);
        this.config = config;
        password = p; creator = c; listener = l;
        areaThread = new Thread(this);
        responseManager = new ResponseManager(this);
        phaseManager = config.async ? new PhaseManager(this) : new PhaseManagerSimple(this);
        action(ActionType.creation);
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
        if (checkPurge()) stopArea(true);
    }

    @Override
    public void handleRoomJoin(Occupant occupant, ZugRoom prevRoom, ZugRoom newRoom) {}

    public void setPurgeAway(boolean purgeAway) {
        config.purgeAway = purgeAway;
    }

    public void setPurgeDeserted(boolean purgeDeserted) {
        config.purgeDeserted = purgeDeserted;
    }

    public boolean isBumpAway() {
        return config.bumpAway;
    }

    public void setBumpAway(boolean bumpAway) {
        config.bumpAway = bumpAway;
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
        if (conn == null || isOccupant(conn,false)) return false; else action(ActionType.obs);
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
            optionsManager = new OptionsManager(node); return true;
        } catch (Exception e) { err(user,"Error setting options: " + e.getMessage() + ", json: " + node); }
        else err(user, "Permission denied(not creator)");
        return false;
    }

    /**
     * Update a user of a (presumably changed) Option.
     * @param user the ZugUser to update
     */
    public void updateOptions(ZugUser user) {
        user.tell(ZugServMsgType.updateOptions,ZugUtils.newJSON().put(ZugFields.AREA_ID,getTitle()).set(ZugFields.OPTIONS, optionsManager.toJSON()));
    }

    /**
     * Update all Occupants of a (presumably changed) Option.
     */
    public void spamOptions() {
        spam(ZugServMsgType.updateOptions,ZugUtils.newJSON().set(ZugFields.OPTIONS, optionsManager.toJSON()));
    }
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
            if (checkPurge()) stopArea(true);
            return true;
        }
        return false;
    }

    private boolean checkPurge() {
        return config.purgeDeserted && isDeserted(config.purgeAway);
    }

    public void setRunning(boolean running) { this.running = running; }

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
            action(ActionType.start);
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
            phaseManager.shutdownPhases();
        }
        if (close) getListener().areaClosed(this);
    }

    public boolean nudgeArea(Occupant occupant) {
        if (allowed(occupant.getUser(),OperationType.nudge)) {
            action(ActionType.nudge); return true;
        } return false;
    }

    public boolean allowed(ZugUser user, OperationType t) {
        return switch (t) {
            case start, stop -> isCreator(user);
            case nudge -> isOccupant(user.getConn());
        };
    }

    @Override
    public void run() {
        spam("Running " + getTitle());
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

    /**
     * Sends a message with the default type (ZugFields.ServMsgType.areaMsg).
     * @param occupant the message recipient
     * @param msg an alphanumeric message
     */
    @Override
    public void tell(Occupant occupant, String msg) {
        tell(occupant, ZugServMsgType.areaMsg,msg);
    }

    @Override
    final public void msg(ZugUser user, String msg) {
        user.tell(ZugServMsgType.areaMsg,ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.AREA_ID,getTitle()));
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        ObjectNode node = super.toJSON(scopes);
        if (isBasic(scopes)) {
            node.put(ZugFields.PHASE,phaseManager.getPhase().name())
                    .put(ZugFields.PHASE_TIME_REMAINING,phaseManager.getPhaseTimeRemaining())
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
            node.set(ZugFields.OPTIONS, optionsManager.toJSON());
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
