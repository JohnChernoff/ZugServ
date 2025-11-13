package org.chernovia.lib.zugserv;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages rate limiters for connections, users, and IP addresses.
 *
 * <p><b>Three-tier limiting:</b>
 * <ul>
 *   <li>Connection Rate Limit - per individual connection (prevents connection spam)
 *   <li>User Rate Limit - per authenticated user (prevents account spam)
 *   <li>IP Rate Limit - per client IP (prevents network-wide spam)
 * </ul>
 *
 * <p><b>Default Limits:</b>
 * <ul>
 *   <li>Connection: 100 msgs/sec (burst capacity: 500)
 *   <li>User: 50 msgs/sec (burst capacity: 250)
 *   <li>IP: 200 msgs/sec (burst capacity: 1000)
 * </ul>
 */
public class RateLimitManager {

    // Connection-level limiters (by connection ID)
    private final ConcurrentHashMap<Long, RateLimiter> connLimiters = new ConcurrentHashMap<>();

    // User-level limiters (by unique name)
    private final ConcurrentHashMap<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    // IP-level limiters (by IP address)
    private final ConcurrentHashMap<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();

    // Configuration
    private long connMaxTokens = 500;      // Burst capacity
    private long connTokensPerSec = 100;   // Sustained rate

    private long userMaxTokens = 250;
    private long userTokensPerSec = 50;

    private long ipMaxTokens = 1000;
    private long ipTokensPerSec = 200;

    /**
     * Checks if a connection is allowed to send a message.
     * Applies all three rate limit tiers.
     *
     * @param conn the connection
     * @param user the user (may be null if not logged in)
     * @return true if allowed, false if rate limited
     */
    public boolean allow(Connection conn, ZugUser user) {
        // Check connection limit
        if (!checkConnectionLimit(conn)) {
            return false;
        }

        // Check user limit (if logged in)
        if (user != null && !checkUserLimit(user)) {
            return false;
        }

        // Check IP limit
        if (!checkIPLimit(conn)) {
            return false;
        }

        return true;
    }

    /**
     * Checks connection-level rate limit.
     *
     * @param conn the connection
     * @return true if allowed, false if rate limited
     */
    private boolean checkConnectionLimit(Connection conn) {
        long connId = conn.getID();
        RateLimiter limiter = connLimiters.computeIfAbsent(connId,
                k -> new RateLimiter(connMaxTokens, connTokensPerSec));

        return limiter.allow();
    }

    /**
     * Checks user-level rate limit.
     *
     * @param user the user
     * @return true if allowed, false if rate limited
     */
    private boolean checkUserLimit(ZugUser user) {
        String uniqueName = user.getUniqueName().toString();
        RateLimiter limiter = userLimiters.computeIfAbsent(uniqueName,
                k -> new RateLimiter(userMaxTokens, userTokensPerSec));

        return limiter.allow();
    }

    /**
     * Checks IP-level rate limit.
     *
     * @param conn the connection
     * @return true if allowed, false if rate limited
     */
    private boolean checkIPLimit(Connection conn) {
        String address = conn.getAddress();
        if (address == null || address.equals("0.0.0.0")) {
            return true; // No IP info yet, allow
        }

        RateLimiter limiter = ipLimiters.computeIfAbsent(address,
                k -> new RateLimiter(ipMaxTokens, ipTokensPerSec));

        return limiter.allow();
    }

    /**
     * Cleans up a connection's rate limiter when it disconnects.
     * Prevents memory leaks from accumulated connection IDs.
     *
     * @param connId the connection ID
     */
    public void removeConnection(long connId) {
        connLimiters.remove(connId);
    }

    /**
     * Cleans up a user's rate limiter when they log out.
     *
     * @param user the user
     */
    public void removeUser(ZugUser user) {
        userLimiters.remove(user.getUniqueName().toString());
    }

    // ========================================================================
    // Configuration Methods
    // ========================================================================

    public void setConnectionLimit(long maxTokens, long tokensPerSec) {
        this.connMaxTokens = maxTokens;
        this.connTokensPerSec = tokensPerSec;
    }

    public void setUserLimit(long maxTokens, long tokensPerSec) {
        this.userMaxTokens = maxTokens;
        this.userTokensPerSec = tokensPerSec;
    }

    public void setIPLimit(long maxTokens, long tokensPerSec) {
        this.ipMaxTokens = maxTokens;
        this.ipTokensPerSec = tokensPerSec;
    }

    /**
     * Gets connection limiter for monitoring (null if not created yet).
     */
    public RateLimiter getConnectionLimiter(long connId) {
        return connLimiters.get(connId);
    }

    /**
     * Gets user limiter for monitoring (null if not created yet).
     */
    public RateLimiter getUserLimiter(ZugUser user) {
        return userLimiters.get(user.getUniqueName().toString());
    }

    /**
     * Gets IP limiter for monitoring (null if not created yet).
     */
    public RateLimiter getIPLimiter(String address) {
        return ipLimiters.get(address);
    }
}
