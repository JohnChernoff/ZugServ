package org.chernovia.lib.zugserv;

import java.util.ArrayList;

public abstract class ConnAdapter implements Connection {
	
	private final ArrayList<Integer> channels = new ArrayList<>();
	private int currChan = 0;
	private Ban ban = null;
	private String handle;
	private ZugServ server;
	private boolean auto;
	private Status status;
	private long userID;

	private long latency = 0;
	private long lastPing = System.currentTimeMillis();

	public long lastPing() {
		return lastPing;
	}

	public void setLastPing(long lastPing) {
		this.lastPing = lastPing;
	}

	public long getLatency() {
		return latency;
	}

	public void setLatency(long latency) {
		this.latency = latency;
	}

	public boolean isSameOrigin(Connection conn) {
		if (conn == null) return false; else return (conn.getID() == userID);
	}
	
	public long getID() { return userID; }
	public void setID(long id) { userID = id; }
	
	public void setStatus(Status s) { status = s; }
	public Status getStatus() { return status; }
	
	@Override
	public void setServ(ZugServ serv) { server = serv; }

	@Override
	public ZugServ getServ() { return server; }

	@Override
	public boolean isAuto() { return auto; }

	@Override
	public void automate(boolean a) { auto = a; }

	@Override
	public Ban getBan() { return ban; }

	@Override
	public void ban(long t,long id) { ban = new Ban(getID(),t,getAddress(),id); }
	
	@Override
	public boolean isFlooding(int limit, long span) { return false; }
	
	public ArrayList<Integer> getChannels() { return channels;	}
	public void joinChan(int c) { channels.add(c); }
	public void partChan(int c) { channels.remove(c); }
	public int getCurrentChannel() { return currChan; }
	public void setCurrentChannel(int chan) { currChan = chan; }
	//public boolean inChan(int c) { return channels.contains(new Integer(c)); }

}
