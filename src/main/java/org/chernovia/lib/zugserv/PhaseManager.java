package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Manages area phases with async support.
 *
 * <p><b>Thread Safety:</b> All public methods are thread-safe.
 * Uses a single-threaded executor for phase scheduling.
 *
 * <p><b>Lifecycle:</b>
 * Must call close() or use try-with-resources before area shutdown.
 * Failure to close will leak the scheduler thread.
 *
 * <p><b>Exception Handling:</b> Phase failures complete exceptionally
 * and are logged at WARNING level.
 *
 * @see PhaseStep
 * @see ZugArea
 */
public class PhaseManager implements JSONifier, AutoCloseable {

    private volatile Thread schedulerThread;

    ThreadFactory threadFactory = r -> {
        Thread t = new Thread(r, "ZugPhaseManager-" + area.getDesc());
        t.setDaemon(false);
        schedulerThread = t;
        return t;
    };

    public Thread getSchedulerThread() {
        return schedulerThread;
    }

    public static class PhaseStep {
        public final Enum<?> phase;
        public final int durationMillis;
        private final Supplier<CompletableFuture<Void>> asyncAction;

        public PhaseStep(Enum<?> phase, int durationMillis, Supplier<CompletableFuture<Void>> asyncAction) {
            this.phase = phase;
            this.durationMillis = durationMillis;
            this.asyncAction = asyncAction;
        }

        public PhaseStep(Enum<?> phase, int durationMillis, Runnable syncAction) {
            this(phase, durationMillis, () -> {
                syncAction.run();
                return CompletableFuture.completedFuture(null);
            });
        }

        public PhaseStep(Enum<?> phase, int durationMillis) {
            this(phase, durationMillis, (Supplier<CompletableFuture<Void>>) null);
        }

        public CompletableFuture<Void> runAction() {
            return (asyncAction != null) ? asyncAction.get() : CompletableFuture.completedFuture(null);
        }
    }

    ZugArea area;
    Enum<?> phase = ZugAreaPhase.initializing;
    long phaseStamp = 0;
    long phaseTime = 0;
    ScheduledFuture<?> currentTimeout;
    CompletableFuture<Boolean> currentPhaseFuture;
    CompletableFuture<Void> currentRunnableFuture;
    Runnable onTimeoutAction;
    private boolean isPaused = false;
    private long remainingMillis = 0;
    private long pauseTimestamp = 0;

    // FIX: Named executor with proper thread factory + shutdown tracking
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;

    public PhaseManager(ZugArea area) {
        this.area = area;

        // Create executor with descriptive thread names for debugging
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "ZugPhaseManager-" + area.getDesc());
            t.setDaemon(false);
            return t;
        };

        this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    /**
     * Sets the current phase.
     * @param p current phase
     * @param quietly suppress client notification
     */
    public void setPhase(Enum<?> p, boolean quietly) {
        area.action(Timeoutable.ActionType.phase);
        phase = p;
        if (!quietly) area.spam(ZugServMsgType.phase, toJSON());
    }

    public void setPhase(Enum<?> p, ObjectNode data) {
        setPhase(p, true);
        area.spam(ZugServMsgType.phase, toJSON2(ZugScope.all).set(ZugFields.PHASE_DATA, data));
    }

    public void setPhaseStamp(long t) { phaseStamp = t; }
    public long getPhaseStamp() { return phaseStamp; }

    long getPhaseTimeRemaining() {
        return phaseTime - (System.currentTimeMillis() - getPhaseStamp());
    }

    public Enum<?> getPhase() {
        return phase;
    }

    public boolean isPhase(Enum<?> p) {
        return phase == p;
    }

    private CompletableFuture<Void> runThenDelay(Runnable action, long delayMillis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            action.run();
            scheduler.schedule(() -> future.complete(null), delayMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<Void> runPausableAction(int millis, Runnable onTimeout) {
        cancelPhase();
        currentRunnableFuture = new CompletableFuture<>();
        onTimeoutAction = onTimeout;
        currentTimeout = scheduler.schedule(() -> {
            onTimeout.run();
            currentRunnableFuture.complete(null);
        }, millis, TimeUnit.MILLISECONDS);
        return currentRunnableFuture;
    }

    public void pause() {
        if (isPaused || currentTimeout == null || currentTimeout.isDone()) return;
        isPaused = true;
        pauseTimestamp = System.currentTimeMillis();
        remainingMillis = getPhaseTimeRemaining();
        currentTimeout.cancel(false);
    }

    public void resume() {
        if (!isPaused || remainingMillis <= 0) return;
        isPaused = false;
        phaseStamp = System.currentTimeMillis();
        currentTimeout = scheduler.schedule(() -> {
            if (currentPhaseFuture != null && !currentPhaseFuture.isDone()) {
                currentPhaseFuture.complete(true);
            } else if (currentRunnableFuture != null && !currentRunnableFuture.isDone()) {
                if (onTimeoutAction != null) onTimeoutAction.run();
                currentRunnableFuture.complete(null);
            }
        }, remainingMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isPaused() {
        return isPaused;
    }

    public long getRemainingMillis() {
        return isPaused ? remainingMillis : getPhaseTimeRemaining();
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase) {
        return newPhase(phase, 0, false, null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, ObjectNode data) {
        return newPhase(phase, 0, false, data);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, boolean quietly) {
        return newPhase(phase, 0, quietly, null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, int millis) {
        return newPhase(phase, millis, false, null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, int millis, ObjectNode data) {
        return newPhase(phase, millis, false, data);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> p, int millis, boolean quietly, ObjectNode data) {
        cancelPhase();
        phaseStamp = System.currentTimeMillis();
        phaseTime = millis;
        if (data != null) setPhase(p, data); else setPhase(p, quietly);
        currentPhaseFuture = new CompletableFuture<>();
        currentTimeout = scheduler.schedule(() -> {
            currentPhaseFuture.complete(true);
        }, phaseTime, TimeUnit.MILLISECONDS);
        return currentPhaseFuture;
    }

    public void interruptPhase() {
        if (currentPhaseFuture != null && !currentPhaseFuture.isDone()) {
            currentPhaseFuture.complete(false);
        }
    }

    public void cancelPhase() {
        if (currentTimeout != null) currentTimeout.cancel(false);
        if (currentPhaseFuture != null && !currentPhaseFuture.isDone()) {
            currentPhaseFuture.completeExceptionally(new CancellationException("Phase cancelled"));
        }
    }

    // FIX: Proper cleanup with timeout and interrupt
    /**
     * Shuts down phase management and waits for pending operations to complete.
     * Should be called when the area is closing.
     *
     * <p>Waits up to 5 seconds for graceful shutdown, then forces shutdown.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void shutdownPhases() throws InterruptedException {
        if (closed) return;
        closed = true;

        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                ZugHandler.log(Level.WARNING, "PhaseManager shutdown timeout for area: " + area.getDesc());
                scheduler.shutdownNow();
                // Wait a bit more for forced shutdown
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    ZugHandler.log(Level.SEVERE, "PhaseManager failed to shutdown: " + area.getDesc());
                }
            }
        } catch (InterruptedException e) {
            ZugHandler.log(Level.WARNING, "PhaseManager shutdown interrupted");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    // FIX: AutoCloseable implementation for try-with-resources
    @Override
    public void close() {
        try {
            shutdownPhases();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public CompletableFuture<Void> runPhaseSequence(List<PhaseStep> steps) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        runPhaseStep(steps, 0, result);
        return result;
    }

    private void runPhaseStep(List<PhaseStep> steps, int index, CompletableFuture<Void> result) {
        if (index >= steps.size()) {
            result.complete(null);
            return;
        }

        PhaseStep step = steps.get(index);
        newPhase(step.phase, step.durationMillis)
                .thenCompose(v -> step.runAction())
                .thenRun(() -> runPhaseStep(steps, index + 1, result))
                .exceptionally(ex -> {
                    ZugHandler.log(Level.WARNING, "Phase step failed at index " + index + ": " + step.phase);
                    result.completeExceptionally(ex);
                    return null;
                });
    }

    public CompletableFuture<Void> awaitSpam(String msg, int millis) {
        return runThenDelay(() -> area.spam(msg), millis);
    }

    public CompletableFuture<Void> awaitSpam(Enum<?> type, int millis) {
        return runThenDelay(() -> area.spam(type), millis);
    }

    public CompletableFuture<Void> awaitSpam(Enum<?> type, String msg, int millis) {
        return runThenDelay(() -> area.spam(type, msg), millis);
    }

    public CompletableFuture<Void> awaitSpam(Enum<?> type, ObjectNode msgNode, int millis) {
        return runThenDelay(() -> area.spam(type, msgNode), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, String msg, int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type, msg, exclude), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, ObjectNode msgNode, int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type, msgNode, exclude), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, ObjectNode msgNode, boolean ignoreDeafness,
                                              int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type, msgNode, ignoreDeafness, exclude), millis);
    }

    public static CompletableFuture<Void> chainFutures(List<CompletableFuture<Void>> futures) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CompletableFuture<Void> f : futures) {
            chain = chain.thenCompose(ignored -> f);
        }
        return chain;
    }

    /**
     * Submits arbitrary game logic to run serialized with phase transitions,
     * on the same single thread PhaseManager uses internally. Use this for
     * any player-initiated action that mutates state also touched by phase
     * transitions, to avoid races between the two. Fire-and-forget — failures
     * are logged, not reported to the caller.
     */
    public void submit(Runnable task) {
        try {
            scheduler.execute(() -> runSafely(task));
        } catch (RejectedExecutionException e) {
            ZugHandler.log(Level.WARNING, "submit() rejected — area shut down: " + area.getDesc());
        }
    }

    /**
     * Same as submit(Runnable), but returns a CompletableFuture that completes
     * (or completes exceptionally) once the task finishes, so callers can chain
     * follow-up work or observe failure directly.
     */
    public CompletableFuture<Void> submitAsync(Runnable task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            scheduler.execute(() -> {
                try {
                    task.run();
                    result.complete(null);
                } catch (Exception e) {
                    ZugHandler.log(Level.SEVERE, "Error in submitted task: " + e.getMessage());
                    ZugServ.printStackTrace(e);
                    result.completeExceptionally(e);
                }
            });
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            ZugHandler.log(Level.SEVERE, "Error in submitted task: " + e.getMessage());
            ZugServ.printStackTrace(e);
        }
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.newJSON()
                .put(ZugFields.PHASE_CURRTIME, System.currentTimeMillis())
                .put(ZugFields.PHASE, phase.name())
                .put(ZugFields.PHASE_STAMP, getPhaseStamp())
                .put(ZugFields.PHASE_TIME_REMAINING, getPhaseTimeRemaining());
    }
}
