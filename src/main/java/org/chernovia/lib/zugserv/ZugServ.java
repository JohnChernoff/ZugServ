package org.chernovia.lib.zugserv;

import java.util.Vector;

public interface ZugServ {
	enum ServType { TYPE_SOCK, TYPE_WEBSOCK, TYPE_IRC, TYPE_TWITCH, TYPE_DISCORD, TYPE_UNKNOWN }
	//enum MsgType { txt,login,pwd,log_ok,serv_msg,err_msg,priv_msg,broadcast }
	int NO_CHAN = -1;
	Vector<Connection> getAllConnections(boolean active);
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

/*
	String MSG_TXT = "txt",
	MSG_LOGIN = "login",
	MSG_PASS = "pwd",
	MSG_LOG_SUCCESS = "log_OK",
	MSG_SERV = "serv_msg",
	MSG_ERR = "err_msg",
	MSG_PRIV = "priv_msg",
	MSG_CAST = "broadcast";
 */
