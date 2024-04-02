package org.chernovia.lib.zugserv.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.chernovia.lib.zugserv.*;

public class WebSockServ extends WebSocketServer implements ZugServ {

	public static boolean STACK_TRACE = true;
	private static final Logger logger = Logger.getLogger(WebSockServ.class.getName());
	private ConnListener connListener;
    private Vector<Connection> connections = new Vector<>();
	int port;
    boolean running = false; boolean paused = false;

    public WebSockServ(int p, ConnListener l) {
    	super(new InetSocketAddress(p));
    	port = p; connListener = l;
    }
    
    public Connection getConn(org.java_websocket.WebSocket sock) {
    	for (Connection conn : connections) if (((WebSockConn)conn).getSock().equals(sock)) return conn;
    	return null;
    }
    
	@Override
	public ConnListener getConnListener() { return connListener; }

	@Override
	public Vector<Connection> getAllConnections(boolean active) {
		return connections;
	}
	
	@Override
	public void onOpen(org.java_websocket.WebSocket socket, ClientHandshake handshake) {
		if (getConn(socket) == null) {
	        WebSockConn conn = new WebSockConn(socket);
	        logger.log(Level.INFO,"Incoming Connection at address: " + conn.getSock().getRemoteSocketAddress());
	        connections.add(conn);
			connListener.connected(conn);
		}
		else logger.log(Level.INFO,"Already connected at address: " + socket.getRemoteSocketAddress());
	}

	@Override
	public void onClose(org.java_websocket.WebSocket socket, int code, String reason, boolean remote) {
		Connection conn = getConn(socket);
		if (conn != null) {
			logger.log(Level.INFO,"Closing Connection at address: " + conn.getAddress() + ", reason: " + reason);
			connections.remove(conn);
			connListener.disconnected(conn);
			conn.setStatus(Connection.Status.STATUS_DISCONNECTED);
		}
		else logger.log(Level.INFO,"Unknown disconnection at address: " + socket.getRemoteSocketAddress());
	}

	@Override
	public void onMessage(org.java_websocket.WebSocket socket, String message) {
		Connection conn = getConn(socket);
		if (conn != null) connListener.newMsg(conn, conn.getCurrentChannel(), message);
		else logger.log(Level.INFO,"Unknown connection message: '" + message + 
				"' at address: " + socket.getRemoteSocketAddress());
	}

	@Override
	public void onError(org.java_websocket.WebSocket socket, Exception ex) {
		logger.log(Level.INFO,"Error: '" + ex.getMessage() + "' at " + socket.getRemoteSocketAddress());
		if (STACK_TRACE) ex.printStackTrace();
	}

	@Override
	public void onStart() {
		logger.log(Level.INFO,"Starting zugserv: " + this.getAddress() + ":" + port );
	}
	
	@Override
	public void startSrv() {
		this.start(); running = true;
	}
	
	@Override
	public void stopSrv() {
		try { this.stop(); } 
		catch (IOException | InterruptedException e) { e.printStackTrace(); }
		running = false;
	}	
	
	@Override
	public void setPause(boolean paused) { this.paused = paused; }
	
	@Override
	public boolean isRunning() { return running; }

	@Override
	public boolean isPaused() { return paused; }	
	
	@Override
	public ServType getType() { return ZugServ.ServType.TYPE_WEBSOCK; }

	@Override
	public void broadcast(String type, String msg) {}

	@Override
	public void tch(int ch, String type, String msg) {}

	@Override
	public int getMaxConnections() { return 0; }

	@Override
	public void setMaxConnections(int c) {}

	@Override
	public int getMaxChannels() { return 0;	}

	@Override
	public void setMaxChannels(int c) {}

}
