package org.chernovia.lib.zugserv;

import chariot.Client;
import chariot.ClientAuth;
//import chariot.api.AccountAuth;
import chariot.api.AccountApiAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.web.WebSockServ;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ZugHandler extends ConnListener and encapsulates ZugServ to provide basic server functionality.
 */
abstract public class ZugHandler extends Thread implements ConnListener, JSONifier {

    private static boolean VERBOSE = true; //for enum names vs ordinal
    static final Logger logger = Logger.getLogger("ZugServLog");
    ConcurrentHashMap<String,ZugUser> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<String,ZugArea> areas = new ConcurrentHashMap<>();
    private boolean preserveDisconnectedUsers = true;

    ZugServ serv;

    public ZugHandler(ZugServ.ServType type) { this(type,0); }
    public ZugHandler(ZugServ.ServType type, int port) {
        setLoggingLevel(Level.INFO);
        serv = switch (type) {
            case SOCK, IRC, TWITCH, DISCORD, UNKNOWN -> null; //TODO: implement?
            case WEBSOCK -> new WebSockServ(port,this);
        };
    }

    public static void setLoggingLevel(Level level) {
        logger.setLevel(level); log("Logging Level: " + level);
    }

    public ConcurrentHashMap<String,ZugUser> getUsers() {
        return users;
    }

    public Optional<ZugUser> addOrGetUser(ZugUser user) {
        return Optional.ofNullable(users.putIfAbsent(user.getUniqueName().toString(), user));
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

    public Optional<ZugArea> getAreaByTitle(String title) {
        return Optional.ofNullable(areas.get(title));
    }

    /**
     * Gets all areas a user is currently an occupant of.
     * @param user A ZugUser
     * @return list of ZugAreas
     */
    public List<ZugArea> getAreasByUser(ZugUser user) {
        return getAreas().stream().filter(area -> area.getOccupant(user).isPresent()).toList();
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
        //log("Looking for user: " + name); for (String key : users.keySet()) log(key.toString());
        ZugUser user = users.get(name.toString()); //log("Found: " + user);
        return user == null ? Optional.empty() : Optional.of(user);
    }

    public boolean isPreservingDisconnectedUsers() {
        return preserveDisconnectedUsers;
    }
    
    public void setPreserveDisconnectedUsers(boolean preserveDisconnectedUsers) {
        this.preserveDisconnectedUsers = preserveDisconnectedUsers;
    }

    public void handleLichessLogin(Connection conn, String token) { //log("Handling lichess login");
        if (token == null || token.isEmpty() || token.equals(ZugFields.UNKNOWN_STRING)) {
            log("Login failure: Bad name/token"); err(conn, "Login failure: Bad name/token");
        } else { //log("Logging in with token: " + token);
            ClientAuth client = Client.auth(token);
            AccountApiAuth aa = client.account(); //log("Created account: " + aa);
            if (aa.profile().isPresent()) {
                log("Logging in lichess user: " + aa.profile().get().name());
                handleLogin(conn, new ZugUser.UniqueName(aa.profile().get().name(),ZugFields.AuthSource.lichess),ZugUtils.newJSON().put(ZugFields.TOKEN,token));
            } else {
                log("Login failure: bad token"); err(conn, "Login failure: bad token");
            }
        }
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

    public void tell(Connection conn, Enum<?> type) {
        tell(conn,type,"");
    }
    public void tell(Connection conn, Enum<?> type, String msg) {
        if (conn != null) conn.tell(type,msg);
    }

    public void tell(Connection conn, Enum<?> type, JsonNode msg) {
        if (conn != null) conn.tell(type,msg);
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
     * Completes the login process.
     * @param conn An Internet Connection
     * @param uName a UniqueName
     * @param dataNode login data (in JSON)
     */
    public abstract void handleLogin(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode);

    /**
     * Called upon receipt of a valid JSON-formatted message from a Connection
     * @param conn a Connection
     * @param type the message type (as String)
     * @param dataNode the message content (in JSON)
     */
    public abstract void handleMsg(Connection conn, String type, JsonNode dataNode);

    /**
     * Receives incoming messages from a Connection, handles pongs, and otherwise directs them to handleMsg() if JSON-readable.
     * @param conn the Connection source
     * @param msg the message (typically but not necessarily in JSON format)
     */
    @Override
    public void newMsg(Connection conn, String msg) { //log("New Conn Message: " + msg);
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

    /**
     * Performs basic house-keeping following a disconnection.
     * @param conn The newly disconnected Connection
     */
    public void disconnected(Connection conn) {
        for (ZugUser user : getUsersByConn(conn)) {
            log("Disconnected: " + user.getName());
            user.setLoggedIn(false);
            List<ZugArea> areas = getAreasByUser(user);
            if (!isPreservingDisconnectedUsers() || areas.isEmpty()) {
                areas.forEach(area -> area.dropOccupant(user));
                removeUser(user);
            }
        }
        for (ZugArea area : getAreas()) area.removeObserver(conn);
    }

    /**
     * Returns an Optional String value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of a text field
     * @return Optional String value of text field (including empty strings)
     */
    public static Optional<String> getTxtNode(JsonNode node, String name) {
        return getTxtNode(node, name, false);
    }

    /**
     * Returns an Optional String value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of a text field
     * @param noEmpty treat empty string as Optional.empty
     * @return Optional String value of text field
     */
    public static Optional<String> getTxtNode(JsonNode node, String name, boolean noEmpty) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null || !n.isTextual() || (n.asText().isEmpty() && noEmpty))
            return Optional.empty(); else return Optional.of(n.asText());
    }


    /**
     * Returns an Optional integer value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of an int field
     * @return Optional value of int field
     */
    public static Optional<Integer> getIntNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asInt());
    }

    /**
     * Returns an Optional double value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of a double field
     * @return Optional value of double field
     */
    public static Optional<Double> getDblNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asDouble());
    }

    /**
     * Returns an Optional boolean value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of a boolean field
     * @return Optional value of boolean field
     */
    public static Optional<Boolean> getBoolNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asBoolean());
    }

    /**
     * Returns an Optional JSON value from a field at the top level of a JSON node.
     * @param node JSON container node
     * @param name name of a JSON field
     * @return Optional value of JSON field
     */
    public static Optional<JsonNode> getJSONNode(JsonNode node, String name) {
        if (node == null) return Optional.empty();
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n);
    }

    /**
     * Checks if a String equals an enumerated field.
     * Normally this is a straight case-insensitive String comparison but if
     * verbosity is false this compares the numeric value of the String with the enumeration's ordinal value.
     * @param str the String to compare
     * @param field the enumerated field to compare
     * @return true if equivalent
     */
    public boolean equalsType(String str,Enum<?> field) {
        return (VERBOSE ? str.equalsIgnoreCase(field.name()) : str.equals(String.valueOf(field.ordinal())));
    }

    /**
     * Indicates the verbosity of server's field type comparison. See equalsType() for more details.
     * @return true if verbose (default)
     */
    static boolean isVerbose() { return VERBOSE; }

    /**
     * Sets the verbosity of server's field type comparison. See equalsType() for more details.
     * @param b true for verbose (default)
     */
    static void setVerbose(boolean b) { VERBOSE = b; }

    final public ObjectNode usersToJSON(boolean nameOnly) {
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        users.values().forEach(user -> arrayNode.add(nameOnly ? user.getUniqueName().toJSON() : user.toJSON()));
        return ZugUtils.newJSON().set(ZugFields.USERS,arrayNode);
    }

    final public ObjectNode areasToJSON(boolean showOccupants, ZugUser user) {
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        areas.values().forEach(area -> {
            if (user == null || area.getOccupant(user).isPresent()) arrayNode.add(area.toJSON(showOccupants));
        });
        return ZugUtils.newJSON().set(ZugFields.AREAS,arrayNode);
    }

    public ObjectNode toJSON() {
        return ZugUtils.joinNodes(usersToJSON(true),areasToJSON(true,null));
    }

}
