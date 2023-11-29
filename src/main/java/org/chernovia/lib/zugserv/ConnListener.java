package org.chernovia.lib.zugserv;

public interface ConnListener {
	void newMsg(Connection conn, int channel, String msg);
	void connected(Connection conn);
	void disconnected(Connection conn);
}
