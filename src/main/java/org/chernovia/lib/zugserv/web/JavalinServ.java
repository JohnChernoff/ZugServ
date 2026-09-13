package org.chernovia.lib.zugserv.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import org.chernovia.lib.zugserv.*;
import java.util.*;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavalinServ extends ServAdapter implements ZugServ {
    private final Javalin server;
    private static final Logger logger = LoggerFactory.getLogger(JavalinServ.class);
    private final Map<WsContext, JavalinConn> connections = new HashMap<>();
    private final int port;

    /**
     * Creates a new Javalin Server.
     * @param p the port for incoming connections
     * @param l the connection listener (see ConnListener)
     * @param endpoint the WebSocket endpoint
     * @param hosts allowed CORS hosts
     */
    public JavalinServ(int p, ConnListener l, String endpoint, List<String> hosts) {
        super(l);
        port = p;
        logger.info("Starting Server, port: {}, endpoint: {}, hosts: {}", p, endpoint, hosts);
        server = Javalin.create(config ->
                        {
                            config.jetty.modifyHttpConfiguration(httpConfig -> {
                                httpConfig.setIdleTimeout(30000); // 30 seconds in milliseconds
                            });
                            //config.http.asyncTimeout = 30_000L;
                            config.bundledPlugins.enableCors(cors ->
                                    cors.addRule(it -> {
                                        if (hosts.isEmpty()) it.reflectClientOrigin = true;
                                        else for (String host : hosts) it.allowHost(host);
                                        it.allowCredentials = true;
                                        it.exposeHeader("Authorization"); }));

                        })
                .post("/shutdown", this::handleShutdown)
                .get("/status",this::handleStatus)
                .before(ctx -> {
                    String origin = ctx.header("Origin");
                    // Return the requesting origin back exactly as allowed
                    // No Origin header: you can choose to be more restrictive here
                    ctx.header("Access-Control-Allow-Origin", Objects.requireNonNullElse(origin, "*"));
                    ctx.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
                    ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                })
                .options("/*", ctx -> {
                    ctx.status(200); // Respond to preflight CORS requests
                })
                .ws("/" + endpoint, ws -> {
                    ws.onConnect(ctx -> {
                        try {
                            if (getConn(ctx).isEmpty()) {
                                JavalinConn conn = new JavalinConn(ctx);

                                logger.debug("Incoming connection from {}", conn.getAddress());

                                connections.put(ctx, conn);
                                getConnListener().connected(conn);
                            } else {
                                logger.debug("Already connected at address: {}",
                                        ctx.session.getRemoteAddress());
                            }
                        } catch (Exception e) {
                            logger.error("Error in onConnect", e);
                            ctx.session.close();
                        }
                    });

                    ws.onMessage(ctx -> {
                        try {
                            String message = ctx.message();

                            if (message.length() < getMaxIncomingMessageSize()) {
                                getConn(ctx).ifPresentOrElse(
                                        conn -> getConnListener().newMsg(conn, message),
                                        () -> logger.warn(
                                                "Unknown connection message: {} at address: {}",
                                                message,
                                                ctx.session.getRemoteAddress())
                                );
                            } else {
                                logger.warn("Overly long connection message: {}",
                                        message.length());
                            }
                        } catch (Exception e) {
                            logger.error("Error in onMessage", e);
                        }
                    });

                    ws.onClose(ctx -> {
                        try {
                            // IMPORTANT: Remove from map FIRST, then notify listener
                            Connection conn = connections.remove(ctx);

                            if (conn != null) {
                                logger.debug("Client disconnected: {}", conn.getAddress());
                                getConnListener().disconnected(conn);
                            } else {
                                logger.warn("Unknown client disconnected");
                            }
                        } catch (Exception e) {
                            logger.error("Error in onClose", e);
                        }
                    });
                });
    }

    /**
     * Gets the Connection associated with the provided Session id.
     * @param ctx the Session id
     * @return the Connection
     */
    public Optional<Connection> getConn(WsContext ctx) {
        return Optional.ofNullable(connections.get(ctx));
    }

    /**
     * Returns a list of all Connections.
     * @param active returns only whatever the implmenting server considers "active" connections.
     * @return a list of Connections, at least one for each connected user
     */
    @Override
    public List<Connection> getAllConnections(boolean active) {
        return connections.values().stream()
                .filter(conn -> !active || conn.getStatus() == Connection.Status.STATUS_CONNECTED)
                .map(conn -> (Connection) conn)
                .toList();
    }

    /**
     * Starts the server.
     */
    @Override
    public void startSrv() {
        server.start("127.0.0.1",port);
        setRunning(true);
    }

    /**
     * Stops the server.
     */
    @Override
    public void stopSrv() {
        server.stop();
        setRunning(false);
    }

    /**
     * Returns ZugServ.ServType.WEBSOCK as the server type.
     * @return the tyoe of server (in this case ZugServ.ServType.WEBSOCK)
     */
    @Override
    public ServType getType() { return ServType.WEBSOCK_JAVALIN; }

    @Override
    public void broadcast(Enum<?> type, String msg, boolean active) {
        connections.values().forEach(conn -> {
            if (active || conn.getStatus() == Connection.Status.STATUS_CONNECTED) conn.tell(type,msg);
        });
    }

    @Override
    public void broadcast(Enum<?> type, JsonNode msg, boolean active) {
        connections.values().forEach(conn -> {
            if (active || conn.getStatus() == Connection.Status.STATUS_CONNECTED) conn.tell(type,msg);
        });
    }

    public void handleShutdown(Context ctx) {
        String remoteAddress = ctx.req().getRemoteAddr();
        if (remoteAddress.equals("127.0.0.1") || remoteAddress.equals("::1")) {
            ctx.status(201);
            System.exit(-1);
        }
    }

    public void handleStatus(Context ctx) {
        ZugManager<?,?> mgr = getMgr();
        if (mgr != null) {
            ctx.json(mgr.toJSON2(ZugScope.basic));
        }
    }

}
