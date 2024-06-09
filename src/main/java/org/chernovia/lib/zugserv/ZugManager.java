package org.chernovia.lib.zugserv;

import chariot.Client;
import chariot.ClientAuth;
import chariot.api.AccountAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.web.WebSockServ;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract public class ZugManager extends Thread implements ConnListener, JSONifier {

    static final Logger logger = Logger.getLogger("ZugServLog");
    private static boolean VERBOSE = true; //for enum names vs ordinal

    ConcurrentHashMap<ZugUser.UniqueName,ZugUser> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<String,ZugArea> areas = new ConcurrentHashMap<>();
    ZugServ serv;

    public ZugManager(ZugServ.ServType type) { this(type,0); }
    public ZugManager(ZugServ.ServType type, int port) {
        setLoggingLevel(Level.INFO);
        serv = switch (type) {
            case TYPE_SOCK, TYPE_IRC, TYPE_TWITCH, TYPE_DISCORD, TYPE_UNKNOWN -> null; //TODO: implement?
            case TYPE_WEBSOCK -> new WebSockServ(port,this);
        };
    }

    public static void setLoggingLevel(Level level) {
        logger.setLevel(level); log("Logging Level: " + level);
    }
    public static void setVerbosity(boolean v) {
        VERBOSE = v; log("Verbosity: " + VERBOSE);

    }
    public static boolean getVerbosity() { return VERBOSE; }

    public ConcurrentHashMap<ZugUser.UniqueName,ZugUser> getUsers() {
        return users;
    }

    public Optional<ZugUser> addOrGetUser(ZugUser user) {
        return Optional.ofNullable(users.putIfAbsent(user.getUniqueName(), user));
    }

    public Optional<ZugUser> removeUser(ZugUser user) {
        return Optional.ofNullable(users.remove(user.getName()));
    }

    public Optional<ZugArea> addOrGetArea(ZugArea area) {
        return Optional.ofNullable(areas.putIfAbsent(area.getTitle(), area));
    }

    public Optional<ZugArea> removeArea(ZugArea area) {
        return Optional.ofNullable(areas.remove(area.getTitle()));
    }

    public Collection<ZugArea> getAreas() {
        return areas.values();
    }

    public ZugServ getServ() {
        return serv;
    }

    public void setServ(WebSockServ serv) {
        this.serv = serv;
    }

    // return users.values.filter(user => user.conn.isSameOrigin(conn)).toVector()
    public List<ZugArea> getAreasByUser(ZugUser user) {
        final List<ZugArea> areaList = new Vector<>();
        for (ZugArea area : areas.values()) {
            if (area.occupants.containsKey(user)) areaList.add(area);
        }
        return areaList;
    }

    public Optional<ZugArea> getAreaByTitle(String title) {
        return Optional.ofNullable(areas.get(title));
    }

    public List<ZugUser> getUsersByConn(Connection conn) {
        final List<ZugUser> userList = new Vector<>();
        for (ZugUser user : users.values()) if (user.getConn().equals(conn)) userList.add(user); return userList;
    }

    public Optional<ZugUser> getUserByConn(Connection conn) {
        for (ZugUser user : users.values()) if (user.getConn().equals(conn)) return Optional.of(user);
        return Optional.empty();
    }

    public Optional<ZugUser> getUserByAddress(Connection conn) {
        for (ZugUser user : users.values()) if (user.getConn().isSameOrigin(conn)) return Optional.of(user);
        return Optional.empty();
    }

    public Optional<ZugUser> getUserByName(String name, String source) {
        return getUserByUniqueName(new ZugUser.UniqueName(name,ZugFields.AuthSource.valueOf(source)));
    }

    public Optional<ZugUser> getUserByUniqueName(ZugUser.UniqueName name) {
        ZugUser user = users.get(name);
        return user == null ? Optional.empty() : Optional.of(user);
    }

    public boolean handleLichessLogin(Connection conn, String token) {
        if (token == null || token.isEmpty() || token.equals(ZugFields.UNKNOWN_STRING)) {
            err(conn, "Bad name/token");
        } else { //log("Logging in with token: " + token.asText());
            ClientAuth client = Client.auth(token);
            AccountAuth aa = client.account();
            if (aa.profile().isPresent()) {
                handleLogin(conn, aa.profile().get().name(),ZugFields.AuthSource.lichess,token);
                return true;
            } else {
                err(conn, "Login failure: bad token");
            }
        }
        return false;
    }

    public void spam(String msg) {
        spam(ZugFields.ServMsgType.servMsg,msg);
    }

    public void spam(Enum<?> type,String msg) {
        for (ZugUser user : users.values()) user.tell(type,msg);
    }

    public void spam(Enum<?> type,JsonNode msgNode) {
        for (ZugUser user : users.values()) user.tell(type,msgNode);
    }

    public void broadcast(Enum<?> type,String msg) {
        for (Connection conn : serv.getAllConnections(true)) {
            conn.tell(VERBOSE ? type.name() : String.valueOf(type.ordinal()),msg);
        }
    }

    public void broadcast(Enum<?> type,JsonNode msgNode) {
        for (Connection conn : serv.getAllConnections(true)) {
            conn.tell(VERBOSE ? type.name() : String.valueOf(type.ordinal()),msgNode);
        }
    }

    public void tell(Connection conn, Enum<?> type) {
        tell(conn,type,"");
    }
    public void tell(Connection conn, Enum<?> type, String msg) {
        conn.tell(ZugManager.packType(type),msg);
    }

    public void tell(Connection conn, Enum<?> type, JsonNode msg) {
        conn.tell(ZugManager.packType(type),msg);
    }

    public void err(ZugUser user,String msg) { if (user != null) err(user.getConn(),msg); }
    public void msg(ZugUser user,String msg) { if (user != null) msg(user.getConn(),msg); }

    public abstract void err(Connection conn, String msg);
    public abstract void msg(Connection conn, String msg);

    public static void log(String msg) {
        log(Level.INFO,msg);
    }

    public static void log(String msg, String source) {
        log(Level.INFO,msg,source);
    }

    public static void log(Level level, String msg) {
        log(level,msg,"ZugServ: ");
    }

    public static void log(Level level, String msg, String source) {
        logger.log(level,source + ": " + msg);
    }

    /**
     * Called upon completion of a successful login.
     * @param conn An Internet Connection
     * @param name username
     * @param source the authentication source, if any
     */
    public abstract void handleLogin(Connection conn, String name, ZugFields.AuthSource source, String token);
    public abstract void handleMsg(Connection conn, String type, JsonNode dataNode);
    public void newMsg(Connection conn, int chan, String msg) {
        JsonNode msgNode = ZugUtils.readTree(msg);
        if (msgNode == null) {
            log(Level.WARNING,"Bad JSON message: " + msg); return;
        }
        JsonNode typeNode = msgNode.get("type"), dataNode = msgNode.get("data");
        if (typeNode == null || dataNode == null) {
            err(conn,"Error: Bad Data(null)"); //return;
        }
        else if (equalsType(typeNode.asText(), ZugFields.ClientMsgType.pong)) {
            conn.setLatency(System.currentTimeMillis() - conn.lastPing());
        }
        else {
            handleMsg(conn,typeNode.asText(),dataNode);
        }
    }

    public void disconnected(Connection conn) {
        for (ZugUser user : getUsersByConn(conn)) {
            log("Disconnected: " + user.getName());
            user.setLoggedIn(false);
        }
        for (ZugArea area : getAreas()) area.observers.remove(conn);
    }

    /**
     * @param node JSON container node
     * @param name name of a text field
     * @return Optional String value of text field
     */
    public static Optional<String> getTxtNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null || !n.isTextual()) return Optional.empty(); else return Optional.of(n.asText());
    }

    /**
     * @param node JSON container node
     * @param name name of an int field
     * @return Optional value of int field
     */
    public static Optional<Integer> getIntNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asInt());
    }

    public static Optional<Double> getDblNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asDouble());
    }

    public static Optional<Boolean> getBoolNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asBoolean());
    }

    public static Optional<JsonNode> getJSONNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n);
    }

    public static Optional<JsonNode> getNodes(JsonNode n, String... fields) {
        if (n == null) return Optional.empty();
        JsonNode node = n.deepCopy();
        for (String name : fields) {
            node = node.get(name); if (node == null) return Optional.empty();
        }
        return Optional.of(node);
    }

    public static Optional<String> getStringTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asText());
    }

    public static Optional<Integer> getIntTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asInt());
    }

    public static Optional<Double> getDoubleTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asDouble());
    }

    public static Optional<Boolean> getBoolTree(JsonNode n, String... fields) {
        JsonNode node = getNodes(n, fields).orElse(null);
        if (node == null) return Optional.empty(); else return Optional.of(node.asBoolean());
    }

    public boolean equalsType(String str,Enum<?> field) {
        return (VERBOSE ? str.equalsIgnoreCase(field.name()) : str.equals(String.valueOf(field.ordinal())));
    }

    /**
     * @param field any Enumerated field
     * @return if VERBOSE, the field name, else the field ordinal value as a String
     */
    static String packType(Enum<?> field) {
        return VERBOSE ? field.name() : String.valueOf((field.ordinal()));
    }
    static boolean isVerbose() { return VERBOSE; }
    static void setVerbose(boolean b) { VERBOSE = b; }

    public ObjectNode usersToJSON(boolean nameOnly) {
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        users.values().forEach(user -> arrayNode.add(nameOnly ? user.getUniqueName().toJSON() : user.toJSON()));
        return ZugUtils.newJSON().set(ZugFields.USERS,arrayNode);
    }

    public ObjectNode areasToJSON(boolean listDataOnly) {
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        areas.values().forEach(area -> arrayNode.add(area.toJSON(listDataOnly)));
        return ZugUtils.newJSON().set(ZugFields.AREAS,arrayNode);
    }

    public ObjectNode toJSON() {
        return ZugUtils.joinNodes(usersToJSON(true),areasToJSON(true));
    }

}
