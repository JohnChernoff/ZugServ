package org.chernovia.lib.zugserv;

import java.util.ArrayList;

public interface ZugServ {
	public enum ServType { TYPE_SOCK, TYPE_WEBSOCK, TYPE_IRC, TYPE_TWITCH, TYPE_DISCORD, TYPE_UNKNOWN }
	public static final int NO_CHAN = -1;
	public static final String 
	MSG_TXT = "txt",
	MSG_LOGIN = "login",
	MSG_PASS = "pwd",
	MSG_LOG_SUCCESS = "log_OK",
	MSG_SERV = "serv_msg", 
	MSG_ERR = "err_msg",
	MSG_PRIV = "priv_msg", 
	MSG_CAST = "broadcast";
	ArrayList<Connection> getAllConnections(boolean active);
	void broadcast(String type,String msg);
	void tch(int ch, String type, String msg);
	void startSrv();
	void stopSrv();
	boolean isRunning();
	void setPause(boolean paused);
	boolean isPaused();
	ServType getType();
	int getMaxConnections();
	void setMaxConnections(int c);
	int getMaxChannels();
	void setMaxChannels(int c);
	ConnListener getConnListener();
}
