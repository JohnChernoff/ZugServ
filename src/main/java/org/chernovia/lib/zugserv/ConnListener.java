package org.chernovia.lib.zugserv;

public interface ConnListener {
	void newMsg(Connection conn, int channel, String msg);
	void loggedIn(Connection conn);
	void loggedOut(Connection conn);
	void connected(Connection conn);
	void disconnected(Connection conn);
}
