package org.chernovia.lib.zugserv.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.chernovia.lib.zugserv.*;

/**
 * A Web Socket implementation of the ZugServ interface using org.java_websocket.server.
 */
public class WebSockServ extends WebSocketServer implements ZugServ {

	private final static boolean STACK_TRACE = true;
	private final ConnListener connListener;
	private static final Logger logger = Logger.getLogger(WebSockServ.class.getName());
    private final Map<WebSocket, Connection> connections = new HashMap<>();
	private final int port;
    boolean running = false; boolean paused = false;

	/**
	 * Creates a new Web Socket Server.
	 * @param p the port for incoming connections
	 * @param l the connection listener (see ConnListener)
	 */
    public WebSockServ(int p, ConnListener l) {
    	super(new InetSocketAddress(p));
    	port = p; connListener = l;
    }

	/**
	 * Gets the Connection associated with the provided Web Socket.
	 * @param sock the WebSocket
	 * @return the Connection
	 */
	public Optional<Connection> getConn(org.java_websocket.WebSocket sock) {
		Connection conn = connections.get(sock);
		if (conn == null)  return Optional.empty(); else return Optional.of(conn);
    }

	/**
	 * Gets the connection listener associated with this server.  The listener is responsible for the handling of messaages from Connections.
	 * @return the connection listener.
	 */
	@Override
	public ConnListener getConnListener() { return connListener; }


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
	 * Establishes the initial connection of an incoming user.
	 * @param socket the web socket associated with the user
	 * @param handshake websocket handshake information (currently ignored)
	 */
	@Override
	public void onOpen(org.java_websocket.WebSocket socket, ClientHandshake handshake) {
		if (getConn(socket).isEmpty()) {
	        WebSockConn conn = new WebSockConn(socket);
	        logger.log(Level.INFO,"Incoming Connection at address: " + conn.getSock().getRemoteSocketAddress());
			connections.put(socket,conn); //connections.add(conn);
			connListener.connected(conn);
		}
		else logger.log(Level.INFO,"Already connected at address: " + socket.getRemoteSocketAddress());
	}

	/**
	 * Handles the closing of an established connection.
	 * @param socket the web socket associated with the user
	 * @param code numeric code indicating why the connnection was closed
	 * @param reason A string explanation for the connection's closure
	 * @param remote indicates a remote connection
	 */
	@Override
	public void onClose(org.java_websocket.WebSocket socket, int code, String reason, boolean remote) {
		Connection conn = getConn(socket).orElse(null);
		if (conn != null) {
			logger.log(Level.INFO,"Closing Connection at address: " + conn.getAddress() + ", reason: " + reason);
			connections.remove(socket);
			connListener.disconnected(conn);
			conn.setStatus(Connection.Status.STATUS_DISCONNECTED);
		}
		else logger.log(Level.INFO,"Unknown disconnection at address: " + socket.getRemoteSocketAddress());
	}

	/**
	 * Handles an incoming message and directs it to the server's ConnListener
	 * @param socket the web socket associated with the user
	 * @param message the message being sent (typically in JSON format)
	 */
	@Override
	public void onMessage(org.java_websocket.WebSocket socket, String message) {
		Connection conn = getConn(socket).orElse(null);
		if (conn != null) connListener.newMsg(conn, message);
		else logger.log(Level.INFO,"Unknown connection message: '" + message + 
				"' at address: " + socket.getRemoteSocketAddress());
	}


	/**
	 * Handles errors generated on a given Web Socket.
	 * @param socket the web socket
	 * @param ex the exception
	 */
	@Override
	public void onError(org.java_websocket.WebSocket socket, Exception ex) {
		logger.log(Level.INFO,"Error: '" + ex.getMessage() + "' at " + socket.getRemoteSocketAddress());
		if (STACK_TRACE) ex.printStackTrace();
	}

	/**
	 * Called once the server begins running.
	 */
	@Override
	public void onStart() {
		logger.log(Level.INFO,"Starting zugserv: " + this.getAddress() + ":" + port );
	}

	/**
	 * Starts the server.
	 */
	@Override
	public void startSrv() {
		this.start(); running = true;
	}

	/**
	 * Stops the server.
	 */
	@Override
	public void stopSrv() {
		try { this.stop(); } 
		catch (IOException | InterruptedException e) { e.printStackTrace(); }
		running = false;
	}

	/**
	 * Pauses or unpauses the server.
	 * @param paused true to pause, false to unpause
	 */
	@Override
	public void setPause(boolean paused) { this.paused = paused; }

	@Override
	public boolean isRunning() { return running; }

	@Override
	public boolean isPaused() { return paused; }

	/**
	 * Returns ZugServ.ServType.WEBSOCK as the server type.
	 * @return the tyoe of server (in this case ZugServ.ServType.WEBSOCK)
	 */
	@Override
	public ServType getType() { return ZugServ.ServType.WEBSOCK; }

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

	@Override
	public int getMaxConnections() { return 0; }

	@Override
	public void setMaxConnections(int c) {}

}
