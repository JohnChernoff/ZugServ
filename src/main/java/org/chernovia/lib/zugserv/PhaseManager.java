package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class PhaseManager implements JSONifier {

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
                    result.completeExceptionally(ex);
                    return null;
                });
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
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PhaseManager(ZugArea area) {
        this.area = area;
    }

    /**
     * Sets the current phase.
     * @param p current phase
     * @param quietly suppress client notification
     */
    public void setPhase(Enum<?> p, boolean quietly) {
        area.action(Timeoutable.ActionType.phase);
        phase = p;
        if (!quietly) area.spam(ZugServMsgType.phase, toJSON()); //getListener().areaUpdated(this);
    }

    public void setPhase(Enum<?> p, ObjectNode data) {
        setPhase(p, true);
        area.spam(ZugServMsgType.phase, toJSON2(ZugScope.all).set(ZugFields.PHASE_DATA,data));
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
        cancelPhase();  // Cancel previous phase if any
        currentRunnableFuture = new CompletableFuture<>();
        onTimeoutAction = onTimeout;  // ← stored here
        currentTimeout = scheduler.schedule(() -> {
            onTimeout.run();               // ← invoked later
            currentRunnableFuture.complete(null);
        }, millis, TimeUnit.MILLISECONDS);
        return currentRunnableFuture;
    }

    public void pause() {
        if (isPaused || currentTimeout == null || currentTimeout.isDone()) return;
        isPaused = true;
        pauseTimestamp = System.currentTimeMillis();
        remainingMillis = getPhaseTimeRemaining(); // use same for actions & phases
        currentTimeout.cancel(false);
    }

    public void resume() {
        if (!isPaused || remainingMillis <= 0) return;
        isPaused = false;
        phaseStamp = System.currentTimeMillis(); // reset phaseStamp to now
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
        return newPhase(phase,0,false,null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, ObjectNode data) {
        return newPhase(phase,0,false,data);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, boolean quietly) {
        return newPhase(phase,0,quietly,null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, int millis) {
        return newPhase(phase,millis,false,null);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> phase, int millis, ObjectNode data) {
        return newPhase(phase,millis,false,data);
    }

    public CompletableFuture<Boolean> newPhase(Enum<?> p, int millis, boolean quietly, ObjectNode data) {
        cancelPhase();
        phaseStamp = System.currentTimeMillis();
        phaseTime = millis;
        if (data != null) setPhase(p,data); else setPhase(p, quietly);
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

    public void shutdownPhases() {
        scheduler.shutdownNow();
    }

    public CompletableFuture<Void> awaitSpam(String msg, int millis) {
        return runThenDelay(() -> area.spam(msg), millis);
    }

    public CompletableFuture<Void> awaitSpam(Enum<?> type, int millis) {
        return runThenDelay(() -> area.spam(type), millis);
    }

    public CompletableFuture<Void> awaitSpam(Enum<?> type, String msg, int millis) {
        return runThenDelay(() -> area.spam(type,msg), millis);
    }


    public CompletableFuture<Void> awaitSpam(Enum<?> type,  ObjectNode msgNode, int millis) {
        return runThenDelay(() -> area.spam(type,msgNode), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, String msg, int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type,msg,exclude), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, ObjectNode msgNode, int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type,msgNode,exclude), millis);
    }

    public CompletableFuture<Void> awaitSpamX(Enum<?> type, ObjectNode msgNode, boolean ignoreDeafness,int millis, Occupant... exclude) {
        return runThenDelay(() -> area.spamX(type,msgNode,ignoreDeafness,exclude), millis);
    }

    public static CompletableFuture<Void> chainFutures(List<CompletableFuture<Void>> futures) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (CompletableFuture<Void> f : futures) {
            chain = chain.thenCompose(ignored -> f);
        }
        return chain;
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.newJSON()
                .put(ZugFields.PHASE,phase.name())
                .put(ZugFields.PHASE_STAMP,getPhaseStamp())
                .put(ZugFields.PHASE_TIME_REMAINING,getPhaseTimeRemaining());
    }

}
