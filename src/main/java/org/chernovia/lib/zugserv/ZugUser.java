package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;


abstract public class ZugUser extends Timeoutable implements JSONifier {
    Connection conn;
    UniqueName uniqueName;
    boolean loggedIn;
   // long lastMessage = Long.MAX_VALUE;

    public record UniqueName(String name, ZugFields.AuthSource source) {}

    Set<ZugArea> areas = Collections.synchronizedSet(new LinkedHashSet<>());

    public ZugUser(Connection c, String n, ZugFields.AuthSource source) {
        setConn(c); uniqueName = new UniqueName(n,source); loggedIn = true;

    }

    public Connection getConn() { return conn; }
    public void setConn(Connection c) {
        conn = c;
    }

    public ZugFields.AuthSource getSource() { return uniqueName.source; }

    public UniqueName getUniqueName() {
        return uniqueName;
    }

    public String getName() {
        return uniqueName.name;
    }

    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean b) { loggedIn = b; }

    public boolean addArea(ZugArea area) {
        return areas.add(area);
    }

    public void tell(String msg) {
        tell(ZugFields.ServMsgType.servMsg,msg);
    }
    public void tell(Enum<?> t) { tell(t,""); }

    public void tell(Enum<?> t, String msg) {
        if (loggedIn && conn != null) conn.tell(ZugManager.getVerbosity() ? t.name() : String.valueOf(t.ordinal()),msg);
    }

    public void tell(Enum<?> t, JsonNode json) {
        if (loggedIn && conn != null) conn.tell(ZugManager.getVerbosity() ? t.name() : String.valueOf(t.ordinal()),json);
    }

    public ObjectNode toJSON() { return toJSON(false); }
    public ObjectNode toJSON(boolean nameOnly) {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        if (!nameOnly) {
            node.put(ZugFields.LOGGED_IN,loggedIn); //TODO: time connected, etc.
            node.put(ZugFields.SOURCE,uniqueName.source.name());
        }
        node.put(ZugFields.NAME,uniqueName.name);
        return node;
    }
}
