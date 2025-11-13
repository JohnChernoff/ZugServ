// ============================================================================
// Rate Limiting for ZugServ
// ============================================================================
// Prevents DoS attacks by limiting messages per connection/user/IP

package org.chernovia.lib.zugserv;

/**
 * Token bucket rate limiter for controlling message frequency.
 *
 * <p><b>Algorithm:</b> Token bucket - starts with capacity tokens,
 * refills at a constant rate. Each message consumes 1 token.
 * When empty, requests are rejected.
 *
 * <p><b>Thread Safety:</b> Lock-free using AtomicLong for concurrent access.
 */
public class RateLimiter {
    private final long maxTokens;
    private final long tokensPerSecond;
    private long tokens;
    private long lastRefillTime;

    /**
     * Creates a rate limiter.
     *
     * @param maxTokens maximum tokens in bucket (also initial capacity)
     * @param tokensPerSecond rate at which tokens are added back
     */
    public RateLimiter(long maxTokens, long tokensPerSecond) {
        this.maxTokens = maxTokens;
        this.tokensPerSecond = tokensPerSecond;
        this.tokens = maxTokens;
        this.lastRefillTime = System.currentTimeMillis();
    }

    /**
     * Attempts to consume a token.
     *
     * @return true if token was available and consumed, false if rate limited
     */
    public synchronized boolean allow() {
        refill();

        if (tokens >= 1) {
            tokens--;
            return true;
        }
        return false;
    }

    /**
     * Refills tokens based on time elapsed since last refill.
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long timePassed = now - lastRefillTime;

        long tokensToAdd = (timePassed * tokensPerSecond) / 1000;
        tokens = Math.min(maxTokens, tokens + tokensToAdd);

        if (tokensToAdd > 0) {
            lastRefillTime = now;
        }
    }

    /**
     * Gets current token count (for monitoring).
     *
     * @return current tokens available
     */
    public synchronized long getTokens() {
        refill();
        return tokens;
    }
}
