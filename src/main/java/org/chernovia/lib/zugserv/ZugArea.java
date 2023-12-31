package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;


abstract public class ZugArea extends ZugRoom {

    final AreaListener listener;
    String password;
    ZugUser creator;
    final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    ObjectNode options = ZugUtils.JSON_MAPPER.createObjectNode();

    public ZugArea(String t, ZugUser c, AreaListener l) {
        this(t,ZugFields.UNKNOWN_STRING,c, l);
    }

    public ZugArea(String t, String p, ZugUser c, AreaListener l) {
        title = t; password = p; creator = c; listener = l; //l.areaCreated(this);
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

    public ObjectNode getOptions() { return options; }

    public Optional<Integer> getOptInt(String field) { return ZugManager.getIntNode(options,field); }

    public Optional<String> getOptTxt(String field) { return ZugManager.getTxtNode(options,field); }

    public void setOption(String field, int v) {
        options.put(field,v);
    }

    public void setOption(String field, String s) {
        options.put(field,s);
    }

    public void setOptions(ObjectNode options) { this.options = options; }

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
        user.tell(ZugFields.ServTypes.areaMsg,ZugUtils.makeTxtNode
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
