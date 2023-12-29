package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;


abstract public class ZugArea extends ZugRoom {

    String password;
    ZugUser creator;
    final Set<Connection> observers =  Collections.synchronizedSet(new HashSet<>());
    ObjectNode options = ZugManager.JSON_MAPPER.createObjectNode();

    public ZugArea(String t, ZugUser c) {
        this(t,ZugFields.UNKNOWN_STRING,c);
    }

    public ZugArea(String t, String p, ZugUser c) {
        title = t; password = p; creator = c;
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

    public boolean removeObserver(Connection conn) {
        return observers.remove(conn);
    }

    public ObjectNode getOptions() { return options; }

    public Optional<Integer> getOptInt(String field) { return ZugManager.getIntNode(options,field); }

    public Optional<String> getOptTxt(String field) { return ZugManager.getOptionalTxtNode(options,field); }

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

    abstract public void msg(ZugUser user, String msg);
    abstract public void err(ZugUser user, String msg);
    abstract public void log(String msg);

}
