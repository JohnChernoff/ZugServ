package org.chernovia.lib.zugserv;

/**
 * A ConnListener responds to events relating to a Connection.
 */
public interface ConnListener {

	/**
	 * Handles an incoming message.
	 * @param conn the Connection source
	 * @param msg the message (typically but not necessarily in JSON format)
	 */
	void newMsg(Connection conn, String msg);

	/**
	 * Called uopn initial establishment of a connection (for example: post-handshake, pre-login/password/etc).
	 * @param conn The newly created Connection
	 */
	void connected(Connection conn);

	/**
	 * Called upon discconection of a Connection.
	 * @param conn The newly disconnected Connection
	 */
	void disconnected(Connection conn);
}
