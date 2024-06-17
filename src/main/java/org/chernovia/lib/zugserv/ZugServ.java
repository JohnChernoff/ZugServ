package org.chernovia.lib.zugserv;

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
	 * Denotes a nonexistent channel.
	 */
	int NO_CHAN = -1;

	/**
	 *
	 * @param active returns only whatever the implmenting server considers "active" connections.
	 * @return A list of all connected users.
	 */
	List<Connection> getAllConnections(boolean active);

	/**
	 * Send a message to all connected users.
	 * @param type the server message type
	 * @param msg a plaintext message
	 */
	void broadcast(ZugFields.ServMsgType type,String msg);

	/**
	 * Send a message to a channel. Currently not well supported.
	 * @param channelNumber a number uniquely identifying a given channel (typically an array index)
	 * @param type the server message type
	 * @param msg a plaintext message
	 */
	void tch(int channelNumber, ZugFields.ServMsgType type, String msg);


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
	 * Pauses or unpauses (resumes) the server, depending on the paused parameter.
	 * @param paused
	 */
	void setPause(boolean paused);

	boolean isPaused();

	/**
	 *
	 * @return the server transport type
	 */
	ServType getType();
	int getMaxConnections();
	void setMaxConnections(int c);
	int getMaxChannels();
	void setMaxChannels(int c);

	/**
	 * Returns the listener of this server (generally a ZugHandler or ZugManager)
	 * @return the Connection Listener Object
	 */
	ConnListener getConnListener();
}

