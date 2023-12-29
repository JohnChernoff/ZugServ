package org.chernovia.lib.zugserv;

import chariot.Client;
import chariot.ClientAuth;
import chariot.api.AccountAuth;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.web.WebSockServ;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

abstract public class ZugManager extends Thread implements ConnListener {

    public static boolean VERBOSE = true;
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    ConcurrentHashMap<String,ZugUser> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<String,ZugArea> areas = new ConcurrentHashMap<>();
    WebSockServ serv;

    public ConcurrentHashMap<String,ZugUser> getUsers() {
        return users;
    }

    public ZugUser addOrGetUser(ZugUser user) {
        return users.putIfAbsent(user.getName(),user);
    }

    public ZugUser removeUser(ZugUser user) {
        return users.remove(user.getName());
    }

    public ZugArea addOrGetArea(ZugArea area) {
        return areas.putIfAbsent(area.getTitle(),area);
    }

    public ZugArea removeArea(ZugArea area) {
        return areas.remove(area.getTitle());
    }

    public Collection<ZugArea> getAreas() {
        return areas.values();
    }

    public WebSockServ getServ() {
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

    public ZugArea getAreaByTitle(String title) {
        return areas.get(title);
    }

    public List<ZugUser> getUsersByConn(Connection conn) {
        final List<ZugUser> userList = new Vector<>();
        for (ZugUser user : users.values()) if (user.conn.equals(conn)) userList.add(user); return userList;
    }

    public ZugUser getUserByConn(Connection conn) {
        for (ZugUser user : users.values()) if (user.conn.equals(conn)) return user;
        return null;
    }

    public List<ZugUser> getUsersByName(String name) {
        final List<ZugUser> userList = new Vector<>();
        for (ZugUser user : users.values()) if (user.getName().equals(name)) userList.add(user);
        return userList;
    }

    public ZugUser getUserByName(String name) {
        for (ZugUser user : users.values()) if (user.getName().equals(name)) return user;
        return null;
    }

    public boolean handleLichessLogin(Connection conn, String token) {
        if (token.equals(ZugFields.UNKNOWN_STRING)) {
            err(conn, "Bad name/token");
        } else { //log("Logging in with token: " + token.asText());
            ClientAuth client = Client.auth(token);
            AccountAuth aa = client.account();
            if (aa.profile().isPresent()) {
                lichessLoggedIn(conn, aa.profile().get().name());
                return true;
            } else {
                err(conn, "Login failure: bad token");
            }
        }
        return false;
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

    public void err(ZugUser user,String msg) { err(user.getConn(),msg); }
    public void msg(ZugUser user,String msg) { msg(user.getConn(),msg); }

    public abstract void err(Connection conn, String msg);
    public abstract void msg(Connection conn, String msg);
    public abstract void log(String msg);

    /**
     * Called upon completion of a successful PKCE lichess account authentication.
     * @param conn An Internet Connection
     * @param name Lichess username
     */
    public abstract void lichessLoggedIn(Connection conn, String name);
    public abstract void handleMsg(Connection conn, String type, JsonNode dataNode);
    public void newMsg(Connection conn, int chan, String msg) {
        try {
            JsonNode msgNode = JSON_MAPPER.readTree(msg);
            JsonNode typeNode = msgNode.get("type"), dataNode = msgNode.get("data");
            if (typeNode == null || dataNode == null) {
                err(conn,"Error: Bad Data(null)"); //return;
            }
            else {
                handleMsg(conn,typeNode.asText(),dataNode);
            }
        }
        catch (JsonProcessingException e) {
            log(e.getMessage());
        }
    }

    public void disconnected(Connection conn) {
        for (ZugUser user : getUsersByConn(conn)) {
            log("Disconnected: " + user.getName());
            user.setLoggedIn(false);
        }
        for (ZugArea area : getAreas()) area.observers.remove(conn);
    }

    public String getTxtNode(JsonNode node, String field) {
        return getOptionalTxtNode(node,field).orElse(ZugFields.UNKNOWN_STRING);
    }

    /**
     * @param node JSON container node
     * @param name name of a text field
     * @return Optional String value of text field
     */
    public static Optional<String> getOptionalTxtNode(JsonNode node, String name) {
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asText());
    }

    /**
     * @param node JSON container node
     * @param name name of an int field
     * @return Optional value of int field
     */
    public static Optional<Integer> getIntNode(JsonNode node, String name) {
        JsonNode n = node.get(name);
        if (n == null) return Optional.empty(); else return Optional.of(n.asInt());
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
    @SafeVarargs
    public static ObjectNode makeTxtNode(Map.Entry<String,String>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, String> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeIntNode(Map.Entry<String,Integer>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Integer> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeFloatNode(Map.Entry<String,Float>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Float> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeDoubleNode(Map.Entry<String,Double>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Double> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeBooleanNode(Map.Entry<String,Boolean>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, Boolean> pair : fields) node.put(pair.getKey(),pair.getValue());
        return node;
    }
    @SafeVarargs
    public static ObjectNode makeJSONNode(Map.Entry<String,JsonNode>... fields) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> pair : fields) node.set(pair.getKey(),pair.getValue());
        return node;
    }

    public static ObjectNode joinNodes(ObjectNode... nodes) {
        ObjectNode node = JSON_MAPPER.createObjectNode();
        for (ObjectNode n : nodes) if (n != null) node.setAll(n);
        return node;
    }

    public static <E> Optional<E> getRandomElement (Collection<E> e) {
        return e.stream().skip((int) (e.size() * Math.random())).findFirst();
    }

}
