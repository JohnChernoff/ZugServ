package org.chernovia.lib.zugserv;

/**
 * The ConnAdapter Class performs some generic implementations of the ZugServ interface.
 */
public abstract class ConnAdapter implements Connection {
	
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
	public boolean isFlooding(int limit, long span) { return false; }

}
