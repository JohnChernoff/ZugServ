package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

abstract public class ZugUser extends Timeoutable implements JSONifier {  // long lastMessage = Long.MAX_VALUE;
    private Connection conn;
    private UniqueName uniqueName;
    private boolean loggedIn;
    private String loginToken;
    public record UniqueName(String name, ZugFields.AuthSource source) {
        @Override
        public String toString() {
            return name + (source == ZugFields.AuthSource.none ? "" : ("@" + source.name()));
        }

        public ObjectNode toJSON() {
            return ZugUtils.JSON_MAPPER.createObjectNode().put(ZugFields.SOURCE,source.name()).put(ZugFields.NAME,name);
        }
    }
    Set<ZugArea> areas = Collections.synchronizedSet(new LinkedHashSet<>());

    public ZugUser(Connection c, String name, ZugFields.AuthSource source, String token) {
        setConn(c); uniqueName = new UniqueName(name,source); loggedIn = true; loginToken = token;
    }

    public Connection getConn() { return conn; }
    public void setConn(Connection c) {
        conn = c;
    }
    public ZugFields.AuthSource getSource() { return uniqueName.source; }
    public String getLoginToken() {
        return loginToken;
    }
    public void setLoginToken(String loginToken) {
        this.loginToken = loginToken;
    }
    public void setUniqueName(UniqueName uniqueName) {
        this.uniqueName = uniqueName;
    }
    public UniqueName getUniqueName() {
        return uniqueName;
    }
    public String getName() {
        return uniqueName.name;
    }
    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean b) { loggedIn = b; }

    public boolean isGuest() {
        return getUniqueName().source().equals(ZugFields.AuthSource.none);
    }

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

    //public ObjectNode toJSON() { return toJSON(false); }
    public ObjectNode toJSON() {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        node.put(ZugFields.LOGGED_IN,loggedIn);
        node.set(ZugFields.UNAME,uniqueName.toJSON());
        return node;
    }
}
