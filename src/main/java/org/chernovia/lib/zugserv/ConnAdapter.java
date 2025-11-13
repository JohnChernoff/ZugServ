package org.chernovia.lib.zugserv;

/**
 * The ConnAdapter Class performs generic implementations of the Connection interface.
 *
 * <p><b>Address Handling:</b>
 * <ul>
 *   <li>Server captures remote address automatically on connection
 *   <li>Client can override address via IP message (typically their public IP)
 *   <li>Address is immutable after initial login to prevent spoofing
 *   <li>Both server and client addresses are logged for debugging
 * </ul>
 */
abstract public class ConnAdapter implements Connection {

	private ZugServ server;
	private boolean auto;
	private Status status;
	private long userID;
	long connectionTimeStamp = System.currentTimeMillis();
	private long latency = 0;
	private long lastPing = System.currentTimeMillis();

	// Server-side address capture
	private String remoteAddress = null;

	// Client-reported address (typically public IP)
	private String clientReportedAddress = null;

	// Once a user logs in, freeze the address to prevent spoofing
	private boolean addressLocked = false;

	public long getTimeConnected() {
		return System.currentTimeMillis() - connectionTimeStamp;
	}

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
		if (conn == null) return false;
		else return (conn.getID() == userID);
	}

	public long getID() {
		return userID;
	}

	public void setID(long id) {
		userID = id;
	}

	public void setStatus(Status s) {
		status = s;
	}

	public Status getStatus() {
		return status;
	}

	@Override
	public void setServ(ZugServ serv) {
		server = serv;
	}

	@Override
	public ZugServ getServ() {
		return server;
	}

	@Override
	public boolean isAuto() {
		return auto;
	}

	@Override
	public void automate(boolean a) {
		auto = a;
	}

	@Override
	public boolean isFlooding(int limit, long span) {
		return false;
	}

	// ========================================================================
	// NEW: Address handling with server fallback and validation
	// ========================================================================

	/**
	 * Sets the server-captured remote address (called during connection setup).
	 * This is the address the connection appears to come from on the network.
	 *
	 * @param address remote address from the server's perspective
	 */
	@Override
	public void setRemoteAddress(String address) {
		if (address != null && !address.isBlank()) {
			this.remoteAddress = address;
		}
	}

	/**
	 * Gets the server-captured remote address.
	 *
	 * @return remote address, or null if not captured
	 */
	@Override
	public String getRemoteAddress() {
		return remoteAddress;
	}

	/**
	 * Sets the client-reported address (called when client sends IP message).
	 * This is typically the client's public IP address.
	 *
	 * <p><b>FIX:</b> Validates the address and prevents changes after login.
	 *
	 * @param address client-reported address to set
	 * @return true if successfully set, false if invalid or locked
	 */
	@Override
	public boolean setClientReportedAddress(String address) {
		// Once locked (user logged in), prevent changes to prevent spoofing
		if (addressLocked) {
			ZugHandler.log(java.util.logging.Level.WARNING,
					"Address change attempted after login for connection " + userID);
			return false;
		}

		// Validate the address format
		if (!IPAddressValidator.isValidIP(address)) {
			ZugHandler.log(java.util.logging.Level.WARNING,
					"Invalid IP address format reported by client: " + address);
			return false;
		}

		this.clientReportedAddress = address;
		return true;
	}

	/**
	 * Gets the client-reported address.
	 *
	 * @return client-reported address, or null if not set
	 */
	@Override
	public String getClientReportedAddress() {
		return clientReportedAddress;
	}

	/**
	 * Locks the address after user login to prevent mid-session address spoofing.
	 * Called by ZugUser when login completes.
	 */
	@Override
	public void lockAddress() {
		this.addressLocked = true;
		ZugHandler.log(java.util.logging.Level.FINE,
				"Address locked for connection " + userID +
						" - Remote: " + remoteAddress + ", Client: " + clientReportedAddress);
	}

	/**
	 * Gets the effective address for this connection.
	 * Prefers client-reported address if available, falls back to remote address.
	 * This is the address used for bans and identity tracking.
	 *
	 * @return effective address, or "0.0.0.0" if none available
	 */
	@Override
	public String getAddress() {
		if (clientReportedAddress != null && !clientReportedAddress.isBlank()) {
			return clientReportedAddress;
		}
		if (remoteAddress != null && !remoteAddress.isBlank()) {
			return remoteAddress;
		}
		return "0.0.0.0"; // Fallback for connections without address info
	}

	/**
	 * Sets the address (legacy method for compatibility).
	 * Delegates to setClientReportedAddress() with validation.
	 *
	 * @param address address to set
	 */
	@Override
	public void setAddress(String address) {
		setClientReportedAddress(address);
	}
}

