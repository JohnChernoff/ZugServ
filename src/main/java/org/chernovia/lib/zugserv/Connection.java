package org.chernovia.lib.zugserv;

import java.net.InetAddress;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public interface Connection {
	enum Status {
		STATUS_DISCONNECTED, STATUS_ERR, STATUS_OK, STATUS_LOGIN, STATUS_PASS, STATUS_CLOSING
	}
	String UNKNOWN_HANDLE = "?";
	ZugServ getServ();
	void setServ(ZugServ serv);
	long getID();
	void setID(long id);
	//String getHandle();	void setHandle(String h);
	InetAddress getAddress();
	boolean isSameOrigin(Connection conn);
	boolean isAuto();
	void automate(boolean a);
	void ban(long t,long id);
	Ban getBan();
	boolean isFlooding(int limit, long span);
	void close();
	void tell(String type, String msg);
	void tell(String type, JsonNode msg);
	Status getStatus();
	void setStatus(Status status);
	List<Integer> getChannels();
	int getCurrentChannel();
	void setCurrentChannel(int chan);
	void joinChan(int chan);
	void partChan(int chan);
}
