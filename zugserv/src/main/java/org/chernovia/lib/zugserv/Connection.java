package org.chernovia.lib.zugserv;

import java.net.InetAddress;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public interface Connection {
	public enum Status {
		STATUS_DISCONNECTED, STATUS_ERR, STATUS_OK, STATUS_LOGIN, STATUS_PASS, STATUS_CLOSING;
	}
	public ZugServ getServ();
	public void setServ(ZugServ serv);
	public long getID();
	public void setID(long id);
	public String getHandle();
	public void setHandle(String h);
	public InetAddress getAddress();
	public boolean isSameOrigin(Connection conn);
	public boolean isAuto();
	public void automate(boolean a);
	public void ban(long t,long id);
	public Ban getBan();
	public boolean isFlooding(int limit, long span);
	public void close();
	public void tell(String type, String msg);
	public void tell(String type, JsonNode msg);
	public Status getStatus();
	public void setStatus(Status status);
	public List<Integer> getChannels();
	public int getCurrentChannel();
	public void setCurrentChannel(int chan);
	public void joinChan(int chan);
	public void partChan(int chan);
}
