package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

abstract public class ZugArea extends ZugRoom {

    public Map<Enum<?>,Option> defaults = new HashMap<>();
    public void setDefault(Enum<?> field, Option option) {
        defaults.put(field,option);
    }
    public void initDefaults() {
        for (Enum<?> field : defaults.keySet()) options.set(field.name(),defaults.get(field).toJSON());
    }

    public class Option implements JSONifier {
        public final String text;
        public final boolean boolVal;
        public final int intVal, intMin, intMax;
        public final double dblVal, dblMin, dblMax;
        public Option(String t) {
            text = t; boolVal = false;
            intVal = intMin = intMax = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = Double.MIN_VALUE;
        }
        public Option(boolean bool) {
            text = null; boolVal = bool;
            intVal = intMin = intMax = Integer.MIN_VALUE;
            dblVal = dblMin = dblMax = Double.MIN_VALUE;
        }
        public Option(int i, int min, int max) {
            text = null; boolVal = false;
            intVal = i; intMin = min; intMax = max;
            dblVal = dblMin = dblMax = Double.MIN_VALUE;
        }
        public Option(double d, double min, double max) {
            text = null; boolVal = false;
            intVal = intMin = intMax = Integer.MIN_VALUE;
            dblVal = d; dblMin = min; dblMax = max;
        }

        public ObjectNode toJSON() {
            if (text != null) return optionToJSON(text,null,null);
            else if (intVal != Integer.MIN_VALUE) return optionToJSON(intVal,intMin,intMax);
            else if (dblVal != Double.MIN_VALUE) return optionToJSON(dblVal,dblMin,dblMax);
            else return optionToJSON(boolVal,null,null);
        }
    }

    final AreaListener listener;
    String password;
    ZugUser creator;
    final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    ObjectNode options = ZugUtils.JSON_MAPPER.createObjectNode();

    boolean exists = true;

    public ZugArea(String t, ZugUser c, AreaListener l) {
        this(t,ZugFields.UNKNOWN_STRING,c, l);
    }

    public ZugArea(String t, String p, ZugUser c, AreaListener l) {
        title = t; password = p; creator = c; listener = l; //l.areaCreated(this);
    }

    public boolean exists() {
        return exists;
    }

    public AreaListener getListener() {
        return listener;
    }

    public ZugUser getCreator() {
        return creator;
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

    public boolean isOccupant(Connection conn, boolean byOrigin) {
        for (Occupant occupant : getOccupants()) {
            if (byOrigin) {
                if (occupant.getUser().getConn().isSameOrigin(conn)) return true;
            }
            else if (occupant.getUser().getConn().equals(conn)) return true;
        }
        return false;
    }

    public boolean addObserver(Connection conn) {
        if (isOccupant(conn,true)) return false;
        return observers.add(conn);
    }
    public boolean isObserver(Connection conn) {
        return observers.contains(conn);
    }
    public boolean removeObserver(Connection conn) {
        return observers.remove(conn);
    }

    void handleNoDefault(Enum<?> field) {
        new Error("No Default: " + field.name()).printStackTrace();
    }

    public int getOptInt(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).intVal);
        else { handleNoDefault(field); return 0; }
    }
    public int getOpt(Enum<?> field, int def) {
        return getOptInt(field.name()).orElse(def);
    }

    public double getOptDbl(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).dblVal);
        else { handleNoDefault(field); return 0.0; }
    }
    public double getOpt(Enum<?> field, double def) {
        return getOptDbl(field.name()).orElse(def);
    }

    public boolean getOptBool(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).boolVal);
        else { handleNoDefault(field); return false; }
    }
    public boolean getOpt(Enum<?> field, boolean def) {
        return getOptBool(field.name()).orElse(def);
    }

    public String getOptTxt(Enum<?> field) {
        if (defaults.containsKey(field)) return getOpt(field,defaults.get(field).text);
        else { handleNoDefault(field); return ""; }
    }
    public String getOpt(Enum<?> field, String def) {
        return getOptTxt(field.name()).orElse(def);
    }

    public Optional<Integer> getOptInt(String field) { return ZugManager.getIntTree(options,field,ZugFields.VAL); }
    public Optional<Double> getOptDbl(String field) { return ZugManager.getDoubleTree(options,field,ZugFields.VAL); }
    public Optional<String> getOptTxt(String field) { return ZugManager.getStringTree(options,field,ZugFields.VAL); }
    public Optional<Boolean> getOptBool(String field) { return ZugManager.getBoolTree(options,field,ZugFields.VAL);}

    public void setOptions(ZugUser user, JsonNode node) {
        if (user.equals(creator)) options = (ObjectNode)node; else err(user,"Permission denied(not creator)");
    }
    public void setOption(ZugUser user, String field, String s) {
        if (user.equals(creator)) setOption(field,s); else err(user,"Permission denied(not creator)");
    }
    public void setOption(String field, Object o) {
         options.set(field,optionToJSON(field,o,null,null));
    }
    public ObjectNode optionToJSON(Object o, Number minVal, Number maxVal) {
        return optionToJSON(null,o,minVal,maxVal);
    }
    public ObjectNode optionToJSON(String field, Object o, Number minVal, Number maxVal) {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        if (o instanceof String str) {
            node.put(ZugFields.VAL,str);
        }
        else if (o instanceof Boolean bool) {
            node.put(ZugFields.VAL,bool);
        }
        else if (o instanceof Double d) { //ZugManager.log(field + " -> adding double: " + d);
            node.put(ZugFields.VAL,d);
            if (minVal instanceof Double minDbl) node.put(ZugFields.MIN,minDbl);
            else ZugManager.getDoubleTree(options,field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
            if (maxVal instanceof Double maxDbl) node.put(ZugFields.MAX,maxDbl);
            else ZugManager.getDoubleTree(options,field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
            node.put(ZugFields.INT,false);
        }
        else if (o instanceof Integer i) { //ZugManager.log(field + " -> adding int: " + i);
            node.put(ZugFields.VAL,i);
            if (minVal instanceof Integer minInt) node.put(ZugFields.MIN,minInt);
            else ZugManager.getIntTree(options,field,ZugFields.MIN).ifPresent(min -> node.put(ZugFields.MIN,min));
            if (maxVal instanceof Integer maxInt) node.put(ZugFields.MAX,maxInt);
            else ZugManager.getIntTree(options,field,ZugFields.MAX).ifPresent(max -> node.put(ZugFields.MAX,max));
            node.put(ZugFields.INT,true);
        }
        return node;
    }

    public ObjectNode getOptions() { return options; }

    public void updateOptions(ZugUser user) {
        if (user == null) spam(ZugFields.ServMsgType.updateOptions,ZugUtils.makeJSONNode(Map.entry(ZugFields.OPTIONS,options)));
        else user.tell(ZugFields.ServMsgType.updateOptions,ZugUtils.makeJSONNode(Map.entry(ZugFields.OPTIONS,options)).put(ZugFields.TITLE,title));
    }

    @Override
    public void spamX(Enum<?> t, String msg, Occupant... ignoreList) {
        super.spamX(t,msg,ignoreList);
        for (Connection conn : observers) {
            if (conn.getStatus() == Connection.Status.STATUS_DISCONNECTED) removeObserver(conn);
            else conn.tell(ZugManager.packType(t),msg);
        }
    }

    @Override
    public void spamX(Enum<?> t, ObjectNode msgNode, Occupant... ignoreList) {
        super.spamX(t,msgNode,ignoreList);
        for (Connection conn : observers) {
            if (conn.getStatus() == Connection.Status.STATUS_DISCONNECTED) removeObserver(conn);
            else conn.tell(ZugManager.packType(t),msgNode);
        }
    }

    @Override
    public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.areaMsg,ZugUtils.makeTxtNode
                (Map.entry(ZugFields.MSG,msg),Map.entry(ZugFields.TITLE,getTitle())));
    }

    @Override
    public ObjectNode toJSON(boolean titleOnly) {
        ObjectNode node = super.toJSON(titleOnly);
        if (!titleOnly) {
            node.set(ZugFields.OPTIONS,options);
            node.put(ZugFields.CREATOR,creator != null ? creator.getName() : ""); //or toJSON?
        }
        return node;
    }

}
