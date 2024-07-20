package org.chernovia.lib.zugserv;

import java.net.InetAddress;

/**
 * The Ban class encapsulates information relating to a specific banning of a ZugUser by another ZugUser.
 */
public class Ban {
	final private ZugUser bannedUser;
	final private ZugUser bannor;
	final private long banStart;
	private long banEnd;

	/**
	 * Creates a Ban given a user, duration, and bannor.
	 * @param usr The ZugUser to ban
	 * @param t Duration (in millis) of the ban
	 * @param bannor The ZugUser initiating the ban
	 */
	public Ban(ZugUser usr, long t, ZugUser bannor) { this(usr,System.currentTimeMillis(),t,bannor); }

	/**
	 * Creates a Ban given a user, start time, duration, and bannor.
	 * @param usr The ZugUser to ban
	 * @param startTime When (in millis after Epoch) the Ban begins.
	 * @param t Duration (in millis) of the ban
	 * @param bannor The ZugUser initiating the ban
	 */
	public Ban(ZugUser usr, long startTime, long t,ZugUser bannor) {
		bannedUser = usr; banStart = startTime;
		banEnd = startTime + t;
		this.bannor = bannor;
	}

	/**
	 * Gets the ZugUser responsible for initiating the ban.
	 * @return the bannor (ZugUser responsible for initiating the ban)
	 */
	public ZugUser getBannor() { return bannor; }

	/**
	 * Extends the Ban by a given duration.
	 * @param t the duration (in millis) of the extension
	 */
	public void extend(int t) {
		banEnd = System.currentTimeMillis() + t;
	}

	/**
	 * Gets the current end of the Ban (in millis after Epoch)
	 * @return current time of the Ban's end
	 */
	public long getEnd() { return banEnd; }

	/**
	 * Indicates if the Ban is currently in effect.
	 * @return true if the Ban is currently in effect
	 */
	public boolean inEffect() {
		long t = System.currentTimeMillis();
		return t > banStart && t < banEnd;
	}

	/**
	 * Indicates if the Ban of a given ZugUser is currently in effect.
	 * @param user the possibly banned ZugUser
	 * @return true if the Ban is currently in effect
	 */
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

	/**
	 * Indicates if the current ban affects a specific Connection ID.
	 * @param id the Connection's ID
	 * @return true if matching
	 */
	public boolean match(long id) {
		return id == bannedUser.getConn().getID();
	}

	public String toString() { return bannedUser.getUniqueName() + ": " + bannedUser.getConn().getAddress(); }
}
