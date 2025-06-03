package org.chernovia.lib.zugserv;

import java.util.concurrent.CompletableFuture;

public class PhaseManagerSimple extends PhaseManager {
    public PhaseManagerSimple(ZugArea area) {
        super(area);
    }

    /**
     * Sets a new phase and sleeps for the specified number of seconds or until interrupted.
     * @param p phase to set
     * @param seconds seconds to sleep
     * @return seconds slept
     */
    @Override
    public CompletableFuture<Boolean> newPhase(Enum<?> p, int seconds, boolean quiety) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        phaseTime = seconds * 1000L;
        phaseStamp = System.currentTimeMillis();
        setPhase(p,quiety);
        boolean timeout = true;
        if (seconds > 0) {
            try { Thread.sleep(phaseTime); } catch (InterruptedException e) { timeout = false; }
        }
        future.complete(timeout);
        return future;
    }

    @Override
    public void interruptPhase() {
        Thread thread = area.getAreaThread();
        if (thread != null && thread.getState() == Thread.State.TIMED_WAITING) thread.interrupt();
    }

    @Override
    public void cancelPhase() {
        interruptPhase();
    }

    @Override
    public void shutdownPhases() {  //TODO: join thread?
        interruptPhase();
    }
}
