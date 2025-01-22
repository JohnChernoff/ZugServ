package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

import static org.chernovia.lib.zugserv.ZugHandler.log;

/**
 * ZugArea is a fuller featured extension of ZugRoom that includes passwords, bans, options, phases, and observers.
 */
abstract public class ZugArea extends ZugRoom implements Runnable {

    public enum OperationType {start,stop}

    private final Map<Enum<?>,Option> defaults = new HashMap<>();

    /**
     * Sets a default Option value.
     * @param field An anumerated field describing the Option
     * @param option an Option value
     */
    public void setDefault(Enum<?> field, Option option) {
        defaults.put(field,option);
    }

    /**
     * Initializes the area's options to their default values.
     */
    public void initDefaults() {
        for (Enum<?> field : defaults.keySet()) options.set(field.name(),defaults.get(field).toJSON());
    }

    /**
     * The Option class represents a user settable value that can be either a String, a boolean, an integer, or a double.
     */
    public class Option implements JSONifier {
        public final String text;
        public final boolean boolVal;
        public final int intVal, intMin, intMax, intInc;
        public final double dblVal, dblMin, dblMax, dblInc;

        /**
         * Creates a new String Option.
         * @param t the String text
         */
        public Option(String t) {
            text = t; boolVal = false;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new boolean Option.
         * @param bool the boolean value
         */
        public Option(boolean bool) {
            text = null; boolVal = bool;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new integer Option.
         * @param i the integer value
         * @param min the minimum integer value
         * @param max the maximum integer value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., 2 with min/max values of 0/10 would allow for 0,2,4,6,8,10)
         */
        public Option(int i, int min, int max, int inc) {
            text = null; boolVal = false;
            intVal = i; intMin = min; intMax = max; intInc = inc;
            dblVal = dblMin = dblMax = dblInc = Double.MIN_VALUE;
        }

        /**
         * Creates a new double Option.
         * @param d the double value
         * @param min the minimum double value
         * @param max the maximum double value
         * @param inc the granularity allowed between the minimum and maximum values (e.g., .5 with min/max values of 0/2.5 would allow for 0,.5,1,1.5,2,2.5)
         */
        public Option(double d, double min, double max, double inc) {
            text = null; boolVal = false;
            intVal = intMin = intMax = intInc = Integer.MIN_VALUE;
            dblVal = d; dblMin = min; dblMax = max; dblInc = inc;
        }

        public ObjectNode toJSON() {
            if (text != null) return optionToJSON(text,null,null,null);
            else if (intVal != Integer.MIN_VALUE) return optionToJSON(intVal,intMin,intMax,intInc);
            else if (dblVal != Double.MIN_VALUE) return optionToJSON(dblVal,dblMin,dblMax,dblInc);
            else return optionToJSON(boolVal,null,null,null);
        }
    }

    final private AreaListener listener;
    private String password;
    private ZugUser creator;
    private final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    private final List<Ban> banList = new ArrayList<>();
    private ObjectNode options = ZugUtils.newJSON();
    private boolean exists = true;
    private Enum<?> phase = ZugFields.AreaPhase.initializing;
    private long phaseStamp = 0;
    private long phaseTime = 0;
    private Thread areaThread;
    boolean running = false;

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
        setTitle(t); password = p; creator = c; listener = l; action();
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
        conn.tell(ZugFields.ServMsgType.obs,ZugUtils.newJSON().put(ZugFields.TITLE,getTitle()));
        return observers.add(conn);
    }

    /**
     * Removes an observing Connection.
     * @param conn the Connection
     * @return true if successful
     */
    public boolean removeObserver(Connection conn) {
        if (conn != null) conn.tell(ZugFields.ServMsgType.unObs,ZugUtils.newJSON().put(ZugFields.TITLE,getTitle()));
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
     * Called upon attempted access of a nonexistent Option default value.
     * @param field the Option's descriptor field
     */
    void handleNoDefault(Enum<?> field) {
        new Error("No Default: " + field.name()).printStackTrace();
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
     * Get the integer value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the integer value if any, otherwise either the default value if any, or else zero and handleNoDefault() is called
     */
    public int getOptInt(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).intVal);
        else { handleNoDefault(field); return 0; }
    }

    /**
     * Get the integer value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return  the integer value if any, otherwise either the default value
     */
    public int getOpt(Enum<?> field, int def) {
        return getOptInt(field.name()).orElse(def);
    }

    /**
     * Get the double value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the double value if any, otherwise either the default value if any, or else 0.0 and handleNoDefault() is called
     */
    public double getOptDbl(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).dblVal);
        else { handleNoDefault(field); return 0.0; }
    }

    /**
     * Get the double value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the double value if any, otherwise either the default value
     */
    public double getOpt(Enum<?> field, double def) {
        return getOptDbl(field.name()).orElse(def);
    }

    /**
     * Get the boolean value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the boolean value if any, otherwise either the default value if any, or else false and handleNoDefault() is called
     */
    public boolean getOptBool(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).boolVal);
        else { handleNoDefault(field); return false; }
    }

    /**
     * Get the boolean value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the boolean value if any, otherwise either the default value
     */
    public boolean getOpt(Enum<?> field, boolean def) {
        return getOptBool(field.name()).orElse(def);
    }

    /**
     * Get the String value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @return the String value if any, otherwise either the default value if any, or else "" and handleNoDefault() is called
     */
    public String getOptTxt(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).text);
        else { handleNoDefault(field); return ""; }
    }

    /**
     * Get the String value of the Option associated with the given enumerated field.
     * @param field the enumerated descriptor field
     * @param def a default value
     * @return the String value if any, otherwise either the default value
     */
    public String getOpt(Enum<?> field, String def) {
        return getOptTxt(field.name()).orElse(def);
    }

    private Optional<Integer> getOptInt(String field) { return getIntTree(options,field,ZugFields.VAL); }
    private Optional<Double> getOptDbl(String field) { return getDoubleTree(options,field,ZugFields.VAL); }
    private Optional<String> getOptTxt(String field) { return getStringTree(options,field,ZugFields.VAL); }
    private Optional<Boolean> getOptBool(String field) { return getBoolTree(options,field,ZugFields.VAL);}

    /**
     * Sets all options as JSON-formatted data.
     * @param user the user attempting to set the options
     * @param node the JSON-formatted data
     */
    public void setOptions(ZugUser user, JsonNode node) {
        if (user.equals(creator)) options = (ObjectNode)node; else err(user,"Permission denied(not creator)");
    }

    /**
     * Sets an Option determined from an enumerated field and Object value.
     * @param field the enumerated field
     * @param o the value (either numeric, String, or boolean)
     * @return true if successful
     */
    public boolean setOption(Enum<?> field, Object o) { //options.set(field.name(),optionToJSON(field.name(),o,null,null,null));
        return setOption(field.name(),o);
    }

    /**
     * Sets an Option determined from an alphanumeric field and Object value.
     * @param field an alphanumeric field
     * @param o the value (either numeric, String, or boolean)
     * @return true if successful
     */
    public boolean setOption(String field, Object o) {
        if (o instanceof Number n) {
            JsonNode previousOption = options.get(field);
            if (previousOption != null) {
                JsonNode previousValue = previousOption.get(ZugFields.VAL);
                if (previousValue.isNumber()) {
                    JsonNode minNode = previousOption.get(ZugFields.MIN);
                    JsonNode maxNode = previousOption.get(ZugFields.MAX);
                    if (minNode != null && n.doubleValue() < minNode.asDouble()) return false;
                    else if (maxNode != null && n.doubleValue() > maxNode.asDouble()) return false;
                }
            }
        }
        options.set(field, optionToJSON(field, o, null, null, null));
        return true;
    }

    private ObjectNode optionToJSON(Object o, Number minVal, Number maxVal, Number incVal) {
        return optionToJSON(null,o,minVal,maxVal,incVal);
    }
    private ObjectNode optionToJSON(String field, Object o, Number minVal, Number maxVal, Number incVal) {
        ObjectNode node = ZugUtils.newJSON();
        if (o instanceof String str) {
            node.put(ZugFields.VAL,str);
        }
        else if (o instanceof Boolean bool) {
            node.put(ZugFields.VAL,bool);
        }
        else if (o instanceof Double d) { //ZugManager.log(field + " -> adding double: " + d);
            node.put(ZugFields.VAL,d);
            if (minVal instanceof Double minDbl) node.put(ZugFields.MIN,minDbl);
            else getDoubleTree(options,field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
            if (maxVal instanceof Double maxDbl) node.put(ZugFields.MAX,maxDbl);
            else getDoubleTree(options,field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
            if (incVal instanceof Double inc) node.put(ZugFields.INC,inc);
            else getDoubleTree(options,field,ZugFields.INC).ifPresent(inc -> node.put(ZugFields.INC,inc));
        }
        else if (o instanceof Integer i) { //ZugManager.log(field + " -> adding int: " + i);
            node.put(ZugFields.VAL,i);
            if (minVal instanceof Integer minInt) node.put(ZugFields.MIN,minInt);
            else getIntTree(options,field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
            if (maxVal instanceof Integer maxInt) node.put(ZugFields.MAX,maxInt);
            else getIntTree(options,field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
            if (incVal instanceof Integer inc) node.put(ZugFields.INC,inc);
            else getDoubleTree(options,field,ZugFields.INC).ifPresent(inc -> node.put(ZugFields.INC,inc));
        }
        return node;
    }

    /**
     * Get all options in JSON format.
     * @return JSON-formatted option data
     */
    public ObjectNode getOptions() { return options; }

    /**
     * Update a user of a (presumably changed) Option.
     * @param user the ZugUser to update
     */
    public void updateOptions(ZugUser user) {
        user.tell(ZugFields.ServMsgType.updateOptions,ZugUtils.newJSON().put(ZugFields.TITLE,getTitle()).set(ZugFields.OPTIONS,options));
    }

    /**
     * Update all Occupants of a (presumably changed) Option.
     */
    public void spamOptions() {
        spam(ZugFields.ServMsgType.updateOptions,ZugUtils.newJSON().set(ZugFields.OPTIONS,options));
    }

    private static Optional<JsonNode> getNodes(JsonNode n, String... fields) { //TODO: what's going on here?
        if (n == null) return Optional.empty();
        JsonNode node = n.deepCopy();
        for (String name : fields) {
            node = node.get(name); if (node == null) return Optional.empty();
        }
        return Optional.of(node);
    }

    private static Optional<String> getStringTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asText());
    }

    private static Optional<Integer> getIntTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asInt());
    }

    private static Optional<Double> getDoubleTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asDouble());
    }

    private static Optional<Boolean> getBoolTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asBoolean());
    }

    /**
     * Sets the current phase.
     * @param p current phase
     * @param quietly suppress client notification
     */
    public void setPhase(Enum<?> p, boolean quietly) {
        action();
        phase = p;
        if (!quietly) spam(ZugFields.ServMsgType.phase,phaseToJSON()); //getListener().areaUpdated(this);
    }

    /**
     * Sets a new phase and sleeps for the specified number of seconds or until interrupted.
     * @param p phase to set
     * @param seconds seconds to sleep
     * @return seconds slept
     */
    public boolean newPhase(Enum<?> p, int seconds) {
        setPhase(p,false);
        phaseTime = seconds * 1000L;
        phaseStamp = System.currentTimeMillis();
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

    public boolean isDeserted() {
        return getActiveOccupants().isEmpty();
    }

    @Override
    public boolean dropOccupant(ZugUser user) {
        if (super.dropOccupant(user)) { //log("Dropping: " + user.getUniqueName().toString());
            if (isDeserted()) stopArea(true);
            return true;
        }
        return false;
    }

    public void setRunning(boolean running) { this.running = running; }

    /**
     * Starts an area.  Note this does not send a ZugFields.ServMsgType.startArea message to the client.
     * @param user The user (typicially the creator of the area) starting the area
     * @param initData initialization data (in JSON format)
     * @return true upon success
     */
    public boolean startArea(ZugUser user, JsonNode initData) { //ZugManager.log("Starting: " + getTitle());
        if (!allowed(user,OperationType.start)) {
            err(user,"Permission denied");
        }
        else if (areaThread == null) {
            areaThread = new Thread(this);
            running = true;
            //spam(ZugFields.ServMsgType.startArea,toJSON(true));
            areaThread.start();
            return true;
        }
        else err(user,"Area already started");
        return false;
    }

    public void stopArea(boolean finish) {
        if (areaThread != null) {
            running = false;
            interruptPhase(); //TODO: join thread?
        }
        if (finish) getListener().areaFinished(this);
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
            else conn.tell(t,ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
        }
    }

    @Override
    final public void spamX(Enum<?> t, ObjectNode msgNode, Occupant... ignoreList) {
        super.spamX(t,msgNode,ignoreList);
        for (Connection conn : observers) {
            if (conn.getStatus() == Connection.Status.STATUS_DISCONNECTED) removeObserver(conn);
            else conn.tell(t,msgNode.put(ZugFields.TITLE,getTitle()));
        }
    }

    @Override
    final public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.areaMsg,ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
    }

    @Override
    final public ObjectNode toJSON(boolean showOccupants) {
        return toJSON(showOccupants,false,false);
    }

    final public ObjectNode toJSON(boolean showOccupants, boolean showObservers, boolean showOptions) {
        ObjectNode node = super.toJSON(showOccupants)
                .put(ZugFields.PHASE,getPhase().name())
                .put(ZugFields.PHASE_TIME_REMAINING,getPhaseTimeRemaining())
                .put(ZugFields.EXISTS,exists)
                .put(ZugFields.RUNNING,running)
                .set(ZugFields.CREATOR,creator != null ? creator.getUniqueName().toJSON() : null);
        if (showObservers) {
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            observers.forEach(obs -> arrayNode.add(obs.getID()));
            node.set(ZugFields.OBSERVERS,arrayNode);
        }
        if (showOptions) node.set(ZugFields.OPTIONS,options);
        return node;
    }


}
