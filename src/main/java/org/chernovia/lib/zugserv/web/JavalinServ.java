package org.chernovia.lib.zugserv.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.websocket.WsContext;
import org.chernovia.lib.zugserv.ConnListener;
import org.chernovia.lib.zugserv.Connection;
import org.chernovia.lib.zugserv.ServAdapter;
import org.chernovia.lib.zugserv.ZugServ;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavalinServ extends ServAdapter implements ZugServ {

    private final Javalin server;
    private static final Logger logger = Logger.getLogger(JavalinServ.class.getName());
    private final Map<WsContext, Connection> connections = new HashMap<>();
    int port;

    /**
     * Creates a new Javalin Server.
     * @param p the port for incoming connections
     * @param l the connection listener (see ConnListener)
     */
    public JavalinServ(int p, ConnListener l, List<String> hosts) {

        super(l); port = p;
        server = Javalin.create(config -> config.bundledPlugins.enableCors(cors ->
                cors.addRule(it -> {
                    if (hosts.isEmpty()) it.reflectClientOrigin = true;
                    else for (String host : hosts) it.allowHost(host);
                    it.allowCredentials = true;
                    it.exposeHeader("Authorization");
                })))
                .post("/twitchsrv/shutdown", this::handleShutdown)
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
                .ws("/ws", ws -> {
                            ws.onConnect(ctx -> {
                                System.out.println("Client connected: " + ctx.session.getRemoteAddress());
                                if (getConn(ctx).isEmpty()) {
                                    JavalinConn conn = new JavalinConn(ctx);
                                    logger.log(Level.INFO,"Incoming Connection at address: " + conn.getAddress());
                                    connections.put(ctx,conn);
                                    getConnListener().connected(conn);
                                }
                                else logger.log(Level.INFO,"Already connected at address: " + ctx.session.getRemoteAddress());
                            });
                            ws.onMessage(ctx -> {
                                String message = ctx.message();
                                getConn(ctx).ifPresentOrElse(
                                        conn -> getConnListener().newMsg(conn, message),
                                        () -> logger.log(Level.WARNING, "Unknown connection message: " +
                                                message + " at address: " + ctx.session.getRemoteAddress()));
                            });
                            ws.onClose(ctx -> {
                                logger.log(Level.INFO,"Client disconnected: " + ctx.session.getRemoteAddress());
                                getConn(ctx).ifPresentOrElse(conn -> getConnListener().disconnected(conn),
                                        () -> logger.warning("Unknown client"));
                            });
                        }
                );
    }

    /**
     * Gets the Connection associated with the provided Session id.
     * @param id the Session id
     * @return the Connection
     */
    public Optional<Connection> getConn(WsContext ctx) {
        Connection conn = connections.get(ctx);
        if (conn == null)  return Optional.empty(); else return Optional.of(conn);
    }

    /**
     * Returns a list of all Connections.
     * @param active returns only whatever the implmenting server considers "active" connections.
     * @return a list of Connections, at least one for each connected user
     */
    @Override
    public List<Connection> getAllConnections(boolean active) {
        if (active) return connections.values().stream().filter(connection -> connection.getStatus().equals(Connection.Status.STATUS_OK)).toList();
        else return connections.values().stream().toList();
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
    public ServType getType() { return ZugServ.ServType.WEBSOCK_DEFAULT; }

    @Override
    public void broadcast(Enum<?> type, String msg, boolean active) {
        connections.values().forEach(conn -> {
            if (active || conn.getStatus() == Connection.Status.STATUS_OK) conn.tell(type,msg);
        });
    }

    @Override
    public void broadcast(Enum<?> type, JsonNode msg, boolean active) {
        connections.values().forEach(conn -> {
            if (active || conn.getStatus() == Connection.Status.STATUS_OK) conn.tell(type,msg);
        });
    }

    public void handleShutdown(Context ctx) {
        String remoteAddress = ctx.req().getRemoteAddr();
        if (remoteAddress.equals("127.0.0.1") || remoteAddress.equals("::1")) {
            ctx.status(201);
            System.exit(-1);
        }
    }

}
