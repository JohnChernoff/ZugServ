package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * ZugServ is a transport independent framework for managing internet connections, i.e., a general purpose server.
 */
public interface ZugServ {
	/**
	 * The server transport type.  Currently only WEBSOCK is supported.
	 */
	enum ServType {SOCK, WEBSOCK, IRC, TWITCH, DISCORD, UNKNOWN}

	/**
	 * Returns a list of all Connections (conditionally filtered by connection status)
	 * @param active returns only whatever the implmenting server considers "active" connections.
	 * @return A list of all connected users.
	 */
	List<Connection> getAllConnections(boolean active);

	/**
	 * Send an alphanumeric message to all connected users.
	 * @param type the enumerated server message type
	 * @param msg a plaintext message
	 * @param active if true, sends only to "active" users
	 */
	void broadcast(Enum<?> type,String msg, boolean active);

	/**
	 * Send a JSON-encoded message to all connected users.
	 * @param type the enumerated server message type
	 * @param msg a JSON-encoded message
	 * @param active if true, sends only to "active" users
	 */
	void broadcast(Enum<?> type, JsonNode msg, boolean active);

	/**
	 * Starts the server, which typically means to begin listening for incoming connections, etc.
	 */
	void startSrv();

	/**
	 * Stops the server, which typically means to stop listening for incoming connections and clean up sundry resources.
	 */
	void stopSrv();

	boolean isRunning();

	/**
	 * Pauses or unpauses (resumes) the server.
	 * @param paused true for paused, false for unpaused/resumed
	 */
	void setPause(boolean paused);

	boolean isPaused();

	/**
	 *
	 * @return the server transport type
	 */
	ServType getType();

	/**
	 * Returns the maximum number of allowed connections.
	 * @return number of connections
	 */
	int getMaxConnections();

	/**
	 * Sets the maximum number of allowed connections.
	 * @param c number of connections
	 */
	void setMaxConnections(int c);

	/**
	 * Returns the listener of this server (generally a ZugHandler or ZugManager)
	 * @return the Connection Listener Object
	 */
	ConnListener getConnListener();
}

