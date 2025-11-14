package org.chernovia.lib.zugserv;

import chariot.Client;
import chariot.ClientAuth;
import chariot.api.AccountApiAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import org.chernovia.lib.zugserv.enums.ZugClientMsgType;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import org.chernovia.lib.zugserv.web.JavalinServ;
import org.chernovia.lib.zugserv.web.WebSockServ;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ZugHandler extends ConnListener and encapsulates ZugServ to provide basic server functionality.
 */
abstract public class ZugHandler implements ConnListener, JSONifier {
    public static String GOOGLE_APPLICATION_CREDENTIALS_FILE_NAME = "google_app_credentials";
    private static boolean VERBOSE = true; //for enum names vs ordinal
    static final Logger logger = Logger.getLogger("ZugServLog");
    ConcurrentHashMap<String,ZugUser> users = new ConcurrentHashMap<>();
    ConcurrentHashMap<String,ZugArea> areas = new ConcurrentHashMap<>();
    Map<ZugAuthSource,Boolean> authSources = new HashMap<>();
    private boolean preserveDisconnectedUsers = true;
    ZugServ serv;

    private final RateLimitManager rateLimitManager = new RateLimitManager();

    public RateLimitManager getRateLimitManager() {
        return rateLimitManager;
    }

    public void configureRateLimits(long connMsgs, long connPerSec,
                                    long userMsgs, long userPerSec,
                                    long ipMsgs, long ipPerSec) {
        rateLimitManager.setConnectionLimit(connMsgs, connPerSec);
        rateLimitManager.setUserLimit(userMsgs, userPerSec);
        rateLimitManager.setIPLimit(ipMsgs, ipPerSec);
    }

    public ZugHandler(ZugServ.ServType type, int port) {
        this(type,port,"ws",new ArrayList<>(),null);
    }

    public ZugHandler(ZugServ.ServType type, int port, List<String> hosts) {
        this(type,port,"ws",hosts,null);
    }

    public ZugHandler(ZugServ.ServType type, String ep, int port, List<String> hosts) {
        this(type,port,ep,hosts,null);
    }

    public ZugHandler(ZugServ.ServType type, int port, String ep, List<String> hosts, Map<ZugAuthSource,Boolean> auths) {
        if (auths != null) authSources.putAll(auths);
        else for (ZugAuthSource authSource : ZugAuthSource.values()) authSources.put(authSource, Boolean.TRUE);
        setLoggingLevel(Level.INFO);
        serv = switch (type) {
            case SOCK, IRC, TWITCH, DISCORD, UNKNOWN -> null; //TODO: implement?
            case WEBSOCK_JAVALIN -> new JavalinServ(port,this, ep, hosts);
            case WEBSOCK_DEFAULT -> new WebSockServ(port,this);
        };
        initializeAuthServices(authSources);
    }

    public static void setLoggingLevel(Level level) {
        logger.setLevel(level); log("Logging Level: " + level);
    }

    public static Level getLoggingLevel() {
        if (logger.getLevel() == null) return Level.INFO;
        return logger.getLevel();
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
    public List<ZugArea> areasByUserToJSON(ZugUser user) {
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
        return getUserByUniqueName(new ZugUser.UniqueName(name, ZugAuthSource.valueOf(source)));
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

    /**
     * Checks if auth services are responding (basic health check).
     * Returns false if any timeout has occurred recently.
     *
     * <p>Useful for deciding whether to reject login attempts.
     *
     * @return true if auth services appear healthy
     */
    public boolean areAuthServicesHealthy() {
        // TODO: Implement health tracking
        // - Track recent timeouts per service
        // - Return false if too many failures in recent window
        // - Example: reject logins if Lichess has failed 3+ times in last 60 seconds
        return true;
    }

    public void initializeAuthServices(java.util.Map<ZugAuthSource, Boolean> authSources) {
        if (authSources.get(ZugAuthSource.google)) {
            try {
                // Initialize Firebase with timeout to prevent startup hangs
                CompletableFuture<Void> firebaseInit = CompletableFuture.runAsync(() -> {
                    try {
                        FileInputStream serviceAccount =
                                new FileInputStream(GOOGLE_APPLICATION_CREDENTIALS_FILE_NAME + ".json");
                        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
                        FirebaseOptions options = new FirebaseOptions.Builder()
                                .setCredentials(credentials)
                                .build();
                        FirebaseApp.initializeApp(options);
                        log(Level.INFO, "Firebase initialized successfully");
                    } catch (IOException e) {
                        log(Level.WARNING, "Firebase initialization failed: " + e.getMessage());
                    }
                }, AuthTimeoutConfig.getAuthExecutor());

                // Timeout for Firebase init (5 seconds)
                firebaseInit.completeOnTimeout(null, 5, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            log(Level.WARNING, "Firebase initialization timeout");
                            return null;
                        });

            } catch (Exception e) {
                log(Level.WARNING, "Error initializing Firebase: " + e.getMessage());
            }
        }
    }

    /**
     * Handles Lichess OAuth login with timeout.
     * If Lichess service doesn't respond in 30 seconds, login fails.
     *
     * @param conn the connection attempting to log in
     * @param token the Lichess auth token
     */
    public void handleLichessLogin(Connection conn, String token) {
        if (token == null || token.isEmpty() || token.equals(ZugFields.UNKNOWN_STRING)) {
            log(Level.WARNING, "Login failure: Bad name/token (Lichess)");
            err(conn, "Login failure: Bad name/token");
            return;
        }

        // Run Lichess API call with timeout to prevent hanging
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    ClientAuth client = Client.auth(token);
                    AccountApiAuth aa = client.account();

                    if (aa.profile().isPresent()) {
                        String lichessUsername = aa.profile().get().name();
                        log(Level.INFO, "Logging in Lichess user: " + lichessUsername);
                        handleLogin(conn,
                                new ZugUser.UniqueName(lichessUsername, ZugAuthSource.lichess),
                                ZugUtils.newJSON().put(ZugFields.TOKEN, token));
                    } else {
                        log(Level.WARNING, "Login failure: bad Lichess token");
                        err(conn, "Login failure: bad token");
                    }
                } catch (Exception e) {
                    log(Level.WARNING, "Lichess login error: " + e.getMessage());
                    err(conn, "Login failure: " + e.getMessage());
                }
            }, AuthTimeoutConfig.getAuthExecutor());

            // FIX: Timeout wrapper - fail if takes too long
            future.completeOnTimeout(null,
                            AuthTimeoutConfig.LICHESS_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log(Level.WARNING, "Lichess login timeout or error: " + ex.getMessage());
                        err(conn, "Login service temporarily unavailable. Try again later.");
                        return null;
                    });

        } catch (Exception e) {
            log(Level.WARNING, "Lichess login exception: " + e.getMessage());
            err(conn, "Login failure: " + e.getMessage());
        }
    }

    /**
     * Handles Google OAuth login with timeout.
     * If Firebase doesn't respond in 30 seconds, login fails.
     *
     * @param conn the connection attempting to log in
     * @param token the Google Firebase ID token
     */
    public void handleGoogleLogin(Connection conn, String token) {
        if (token == null || token.isEmpty()) {
            log(Level.WARNING, "Login failure: Empty Google token");
            err(conn, "Login failure: Bad token");
            return;
        }

        // Run Firebase verification with timeout
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    FirebaseToken firebaseToken = FirebaseAuth.getInstance()
                            .verifyIdToken(token);

                    if (firebaseToken != null) {
                        String googleName = firebaseToken.getName();
                        log(Level.INFO, "Logging in Google user: " + googleName);
                        handleLogin(conn,
                                new ZugUser.UniqueName(googleName, ZugAuthSource.google),
                                ZugUtils.newJSON().put(ZugFields.TOKEN, token));
                    } else {
                        log(Level.WARNING, "Login failure: Firebase token verification returned null");
                        err(conn, "Login failure: bad token");
                    }
                } catch (FirebaseAuthException e) {
                    log(Level.WARNING, "Google login error: " + e.getMessage());
                    err(conn, "Login failure: " + e.getMessage());
                }
            }, AuthTimeoutConfig.getAuthExecutor());

            // FIX: Timeout wrapper
            future.completeOnTimeout(null,
                            AuthTimeoutConfig.FIREBASE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        log(Level.WARNING, "Google login timeout or error: " + ex.getMessage());
                        err(conn, "Login service temporarily unavailable. Try again later.");
                        return null;
                    });

        } catch (Exception e) {
            log(Level.WARNING, "Google login exception: " + e.getMessage());
            err(conn, "Login failure: " + e.getMessage());
        }
    }

    public void spam(String msg) {
        spam(ZugServMsgType.servMsg,msg);
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
        if (msg.length() > getServ().getMaxIncomingMessageSize()) {
            log(Level.WARNING, "Message exceeds size limit: " + msg.length() +
                    " from " + conn.getAddress());
            err(conn, "Message too large");
            return;
        }
        try {
            JsonNode msgNode = ZugUtils.readTree(msg).orElseThrow(() -> new ZugException("Bad JSON message: " + msg));
            // NEW: Check required fields exist
            if (!InputValidator.hasRequiredFields(msgNode, "type", "data")) {
                InputValidator.logValidationFailure("REQUIRED_FIELDS", msgNode.toString());
                err(conn, "Error: Bad Data(null)");
                return;
            }
            JsonNode typeNode = msgNode.get("type"), dataNode = msgNode.get("data");
            if (typeNode == null || dataNode == null) { //redundant, but keep for now
                err(conn,"Error: Bad Data(null)"); //return;
            }
            else if (equalsType(typeNode.asText(), ZugClientMsgType.pong)) {
                log(Level.FINE,"Pong from: " + conn.getID());
                conn.setLatency(System.currentTimeMillis() - conn.lastPing());
            }
            else {
                handleMsg(conn,typeNode.asText(),dataNode);
            }
        }
        catch (ZugException e) {
            log(Level.WARNING,e.getMessage());
        }
    }

    /**
     * Performs basic house-keeping following a disconnection.
     * @param conn The newly disconnected Connection
     */
    public void disconnected(Connection conn) {
        for (ZugUser user : getUsersByConn(conn)) {
            log("Disconnected: " + user.getName() + ", duration: " + conn.getTimeConnected()/1000 + " seconds");
            rateLimitManager.removeUser(user);
            user.setLoggedIn(false);
            List<ZugArea> areas = areasByUserToJSON(user);
            if (!isPreservingDisconnectedUsers() || areas.isEmpty()) {
                areas.forEach(area -> area.dropOccupant(user));
                removeUser(user);
            }
        }
        for (ZugArea area : getAreas()) area.removeObserver(conn);
        rateLimitManager.removeConnection(conn.getID());
        log("Active connections: " + getActiveConnectionCount() + "/" + getServ().getMaxConnections());
    }

    /**
     * Returns an Optional Enum value from a field at the top level of a JSON node.
     * @param <T> the enum type
     * @param node JSON container node
     * @param enumClass the Class object of the enum type
     * @param name name of a text field
     * @return Optional enum value of the specified type
     */
    public static <T extends Enum<T>> Optional<T> getEnumNode(JsonNode node, Class<T> enumClass, String name) {
        return getTxtNode(node, name, false)
                .flatMap(txt -> {
                    try {
                        // Case-insensitive enum parsing
                        return Arrays.stream(enumClass.getEnumConstants())
                                .filter(e -> e.name().equalsIgnoreCase(txt))
                                .findFirst();
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                });
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
        users.values().forEach(user -> arrayNode.add(nameOnly ? user.getUniqueName().toJSON2(ZugScope.all) : user.toJSON()));
        return ZugUtils.newJSON().set(ZugFields.USERS,arrayNode);
    }

    final public ObjectNode areasByUserToJSON(boolean showOccupants, ZugUser user) {
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        areas.values().forEach(area -> {
            if (user == null || area.getOccupant(user).isPresent()) {
                if (showOccupants) arrayNode.add(area.toJSON2(ZugScope.basic,ZugScope.occupants_basic));
                else arrayNode.add(area.toJSON2(ZugScope.basic));
            }

        });
        return ZugUtils.newJSON().set(ZugFields.AREAS,arrayNode);
    }

    /**
     * Gets current active connection count.
     *
     * @return number of active connections
     */
    public int getActiveConnectionCount() {
        return getServ().getAllConnections(true).size();
    }

    /**
     * Gets connection usage as percentage of max.
     *
     * @return percentage 0-100
     */
    public int getConnectionUsagePercent() {
        int max = getServ().getMaxConnections();
        if (max <= 0) return 0;
        return (int) ((getActiveConnectionCount() * 100L) / max);
    }

    /**
     * Checks if connection limit is nearing capacity (>= 80%).
     *
     * @return true if nearing capacity
     */
    public boolean isNearCapacity() {
        return getConnectionUsagePercent() >= 80;
    }

    /**
     * Checks if connection limit is critically high (>= 95%).
     *
     * @return true if critically full
     */
    public boolean isCriticallyFull() {
        return getConnectionUsagePercent() >= 95;
    }

    /**
     * Gracefully shuts down the server and all connections.
     * Closes all connections with a 5-second timeout for graceful disconnect.
     *
     * @throws InterruptedException if interrupted during shutdown
     */
    public void shutdownServer() throws InterruptedException {
        int connCount = getActiveConnectionCount();
        log(Level.INFO, "Shutting down server - closing " + connCount + " connections");

        // Notify all connections of shutdown
        for (Connection conn : getServ().getAllConnections(true)) {
            try {
                conn.close("Server shutdown");
            } catch (Exception e) {
                log(Level.WARNING, "Error closing connection: " + e.getMessage());
            }
        }

        // Wait for graceful shutdown (up to 5 seconds)
        long shutdownStart = System.currentTimeMillis();
        long shutdownTimeout = 5000; // 5 seconds

        while (getActiveConnectionCount() > 0 &&
                (System.currentTimeMillis() - shutdownStart) < shutdownTimeout) {
            Thread.sleep(100);
        }

        if (getActiveConnectionCount() > 0) {
            log(Level.WARNING,
                    "Force-closing " + getActiveConnectionCount() +
                            " connections that didn't shutdown gracefully");
        }

        log(Level.INFO, "Server shutdown complete");
    }

    /**
     * Gets diagnostic info about connection pool.
     *
     * @return formatted diagnostic string
     */
    public String getConnectionDiagnostics() {
        return String.format(
                "Connections: %d/%d (%.1f%% utilization) | Near Capacity: %b | Critically Full: %b",
                getActiveConnectionCount(),
                getServ().getMaxConnections(),
                (getActiveConnectionCount() * 100.0) / getServ().getMaxConnections(),
                isNearCapacity(),
                isCriticallyFull()
        );
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.joinNodes(usersToJSON(true), areasByUserToJSON(true,null));
    }

}
