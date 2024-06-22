package org.chernovia.lib.zugserv;

import java.net.InetAddress;

public class Ban {
	private ZugUser bannedUser;
	private ZugUser bannor;
	private long banStart, banEnd;

	public Ban(ZugUser u, long t, ZugUser u2) { this(u,System.currentTimeMillis(),t,u2); }
	public Ban(ZugUser u, long startTime, long t,ZugUser u2) {
		bannedUser = u; banStart = startTime;
		banEnd = startTime + t;
		bannor = u2;
	}
	
	public ZugUser getBannor() { return bannor; }
	
	public void extend(int t) {
		banEnd = System.currentTimeMillis() + t;
	}
	
	public long getEnd() { return banEnd; }
	
	public boolean inEffect() {
		long t = System.currentTimeMillis();
		return t > banStart && t < banEnd;
	}

	public boolean inEffect(ZugUser user) {
		return inEffect() && bannedUser.equals(user);
	}

	private boolean addressMatch(InetAddress a) {
		return addressMatch(a,-1);
	}
	private boolean addressMatch(InetAddress a, int level) {
		if (bannedUser.getConn().getAddress() == null || a == null) return false;
		if (level == -1) return a.equals(bannedUser.getConn().getAddress());
		else {
			for (int i=0;i<level;i++) {
				if (a.getAddress()[i] != bannedUser.getConn().getAddress().getAddress()[i]) return false;
			}
			return true;
		}
	}
	
	public boolean match(long id) {
		return id == bannedUser.getConn().getID();
	}
	
	public String toString() { return bannedUser.getUniqueName() + ": " + bannedUser.getConn().getAddress(); }
}
