package org.chernovia.lib.zugserv;

import java.net.InetAddress;

public class Ban {
	private long banID, bannorID;
	private long banStart, banEnd;
	private InetAddress address;
	
	public Ban(long id, long t,InetAddress a, long bid) { this(id,System.currentTimeMillis(),t,a,bid); }
	public Ban(long id, long startTime, long t, InetAddress a, long bid) {
		banID = id; banStart = startTime; 
		banEnd = startTime + t; address = a;
		bannorID = bid;
	}
	
	public long getBannorID() { return bannorID; }
	
	public void extend(int t) {
		banEnd = System.currentTimeMillis() + t;
	}
	
	public long getEnd() { return banEnd; }
	
	public boolean inEffect() {
		long t = System.currentTimeMillis();
		return t > banStart && t < banEnd;
	}
	
	private boolean addressMatch(InetAddress a, int level) {
		if (address == null || a == null) return false;
		if (level == -1) return a.equals(address);
		else {
			for (int i=0;i<level;i++) {
				if (a.getAddress()[i] != address.getAddress()[i]) return false;
			}
			return true;
		}
	}
	
	public boolean match(long id, InetAddress a) { return match(id,a,-1); }
	public boolean match(long id, InetAddress a, int l) {
		if (a != null) return addressMatch(a,l);
		else return id == banID;
	}
	
	public String toString() { return banID + ": " + address; }
}
