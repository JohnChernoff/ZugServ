package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;


abstract public class ZugUser {
    Connection conn;
    String name;
    boolean loggedIn;

    Set<ZugArea> areas = Collections.synchronizedSet(new LinkedHashSet<>());

    public ZugUser(Connection c, String n) {
        setConn(c); setName(n); loggedIn = true;

    }

    public Connection getConn() { return conn; }
    public void setConn(Connection c) {
        conn = c;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isLoggedIn() { return loggedIn; }
    public void setLoggedIn(boolean b) { loggedIn = b; }

    public boolean addArea(ZugArea area) {
        return areas.add(area);
    }

    public void tell(Enum<?> t) { tell(t,""); }

    public void tell(Enum<?> t, String msg) {
        if (loggedIn && conn != null) conn.tell(ZugManager.VERBOSE ? t.name() : String.valueOf(t.ordinal()),msg);
    }

    public void tell(Enum<?> t, JsonNode json) {
        if (loggedIn && conn != null) conn.tell(ZugManager.VERBOSE ? t.name() : String.valueOf(t.ordinal()),json);
    }

    abstract public ObjectNode toJSON();
}
