// ============================================================================
// PhaseManagerSimple Deprecation Notice
// ============================================================================
// This is the deprecated first implementation of PhaseManager.
// Kept for backward compatibility only.
// New code should use PhaseManager instead.

package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * @deprecated Use {@link PhaseManager} instead.
 *
 * <p>PhaseManagerSimple is the original, synchronous implementation of phase management.
 * It uses blocking Thread.sleep() which is inefficient for long-running servers.
 *
 * <p><b>Known Issues (not fixed due to deprecation):</b>
 * <ul>
 *   <li>Uses blocking Thread.sleep() instead of async scheduling
 *   <li>Can block the entire area thread
 *   <li>Difficult to pause/resume phases
 *   <li>No proper resource cleanup on shutdown
 * </ul>
 *
 * <p><b>Migration:</b> Simply replace PhaseManagerSimple with PhaseManager.
 * The API is backward compatible - no code changes needed beyond the class name.
 *
 * <p>Example:
 * <pre>
 * // Before (old)
 * phaseManager = config.async ? new PhaseManager(this) : new PhaseManagerSimple(this);
 *
 * // After (new)
 * phaseManager = new PhaseManager(this);  // Always use async now
 * </pre>
 *
 * <p>PhaseManager offers:
 * <ul>
 *   <li>Async phase scheduling with ScheduledExecutorService
 *   <li>Proper pause/resume support
 *   <li>AutoCloseable for resource cleanup
 *   <li>Better performance on high-load servers
 *   <li>Thread-safe operations
 * </ul>
 *
 * @see PhaseManager
 * @see ZugArea
 */
@Deprecated(since = "1.1", forRemoval = true)
public class PhaseManagerSimple extends PhaseManager {

    public PhaseManagerSimple(ZugArea area) {
        super(area);
        ZugHandler.log(Level.WARNING,
                "PhaseManagerSimple is deprecated and will be removed in a future version. " +
                        "Use PhaseManager instead for area: " + area.getDesc());
    }

    /**
     * @deprecated Use {@link PhaseManager#newPhase(Enum, int, boolean, ObjectNode)} instead.
     *
     * Sets a new phase and blocks via Thread.sleep() until the phase duration completes.
     * This is inefficient and deprecated.
     *
     * @param p phase to set
     * @param seconds duration in seconds
     * @param quietly suppress client notification
     * @param data optional phase data
     * @return future completing with true if timeout occurred, false if interrupted
     */
    @Override
    @Deprecated(since = "1.1", forRemoval = true)
    public CompletableFuture<Boolean> newPhase(Enum<?> p, int seconds, boolean quietly, ObjectNode data) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        phaseTime = seconds * 1000L;
        phaseStamp = System.currentTimeMillis();
        if (data != null) setPhase(p, data); else setPhase(p, quietly);

        boolean timeout = true;
        if (seconds > 0) {
            try {
                Thread.sleep(phaseTime);
            } catch (InterruptedException e) {
                timeout = false;
                Thread.currentThread().interrupt();
            }
        }
        future.complete(timeout);
        return future;
    }

    @Override
    @Deprecated(since = "1.1", forRemoval = true)
    public void interruptPhase() {
        Thread thread = area.getAreaThread();
        if (thread != null && thread.getState() == Thread.State.TIMED_WAITING) {
            thread.interrupt();
        }
    }

    @Override
    @Deprecated(since = "1.1", forRemoval = true)
    public void cancelPhase() {
        interruptPhase();
    }

    @Override
    @Deprecated(since = "1.1", forRemoval = true)
    public void shutdownPhases() throws InterruptedException {
        interruptPhase();
    }
}
