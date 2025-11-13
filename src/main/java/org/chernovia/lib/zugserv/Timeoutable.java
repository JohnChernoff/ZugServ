// ============================================================================
// ISSUE #3: Timeoutable Thread Safety & Precision
// ============================================================================
// Problem 1: lastActionTimestamp accessed without synchronization
//            Multiple threads can read/write simultaneously causing stale values
// Problem 2: 1-second precision (Instant.now().getEpochSecond())
//            Can miss timeouts by up to 1 second
// Problem 3: idleTimeout uses Integer.MAX_VALUE as default
//            Effectively disables timeout checking for new objects

package org.chernovia.lib.zugserv;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Tracks idle time for Objects to determine if they've exceeded their timeout threshold.
 *
 * <p><b>Thread Safety:</b> Uses AtomicLong for thread-safe timestamp updates without locking.
 * All methods are thread-safe and can be called concurrently.
 *
 * <p><b>Precision:</b> Uses millisecond precision (System.currentTimeMillis()) for accurate
 * timeout detection. 1-second jitter is effectively eliminated.
 *
 * <p><b>Default Timeout:</b> Defaults to no timeout (Integer.MAX_VALUE). Subclasses should
 * set an appropriate idle timeout via setIdleTimeout(int).
 *
 * @see ZugUser
 * @see ZugArea
 */
public abstract class Timeoutable {

    public enum ActionType {
        creation, phase, join, part, start, occupant, obs, nudge, other, user
    }

    // FIX: Use AtomicLong for thread-safe updates without locks
    // Stores milliseconds since epoch (not seconds) for precise timeout detection
    private final AtomicLong lastActionTimestamp = new AtomicLong(System.currentTimeMillis());

    // Idle timeout in seconds (converted to milliseconds for comparison)
    private volatile int idleTimeoutSeconds = Integer.MAX_VALUE;

    /**
     * Gets the idle timeout threshold in seconds.
     *
     * @return idle timeout in seconds (Integer.MAX_VALUE = no timeout)
     */
    public final int getIdleTimeout() {
        return idleTimeoutSeconds;
    }

    /**
     * Sets the idle timeout threshold.
     *
     * <p>Objects are considered timed out if no action occurs within this time period.
     * Set to Integer.MAX_VALUE to disable timeout checking (default).
     *
     * @param timeoutSeconds idle timeout in seconds
     */
    public final void setIdleTimeout(int timeoutSeconds) {
        if (timeoutSeconds < 0) {
            ZugHandler.log(Level.WARNING, "Negative idle timeout ignored: " + timeoutSeconds);
            return;
        }
        this.idleTimeoutSeconds = timeoutSeconds;
    }

    /**
     * Gets the timestamp of the last recorded action (in milliseconds since epoch).
     *
     * @return milliseconds since epoch when last action occurred
     */
    public final long getLastActionTimestamp() {
        return lastActionTimestamp.get();
    }

    /**
     * Records that an action has occurred, updating the last activity timestamp.
     *
     * <p>This should be called whenever the object performs activity that should
     * reset its idle timeout. Thread-safe for concurrent calls.
     *
     * @param type the type of action occurring (for debugging/metrics)
     */
    public void action(Enum<?> type) {
        long now = System.currentTimeMillis();
        lastActionTimestamp.set(now);
    }

    /**
     * Checks if this object has exceeded its idle timeout threshold.
     *
     * <p>Returns false if timeout is disabled (set to Integer.MAX_VALUE).
     * Thread-safe for concurrent checks.
     *
     * @return true if current time exceeds (lastAction + timeout), false otherwise
     */
    public boolean timedOut() {
        // Timeout disabled
        if (idleTimeoutSeconds == Integer.MAX_VALUE) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lastAction = lastActionTimestamp.get();
        long timeoutMillis = (long) idleTimeoutSeconds * 1000;

        return (now - lastAction) > timeoutMillis;
    }

    /**
     * Gets time in seconds since last action.
     * Useful for debugging and metrics.
     *
     * @return seconds elapsed since last action
     */
    public final long getSecondsSinceLastAction() {
        long now = System.currentTimeMillis();
        long lastAction = lastActionTimestamp.get();
        return (now - lastAction) / 1000;
    }

    /**
     * Gets time in milliseconds since last action.
     *
     * @return milliseconds elapsed since last action
     */
    public final long getMillisSinceLastAction() {
        long now = System.currentTimeMillis();
        long lastAction = lastActionTimestamp.get();
        return now - lastAction;
    }
}
