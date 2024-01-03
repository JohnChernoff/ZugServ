package org.chernovia.lib.zugserv;

import java.time.Instant;

public abstract class Timeoutable {

    int idleTimeout = Integer.MAX_VALUE;
    long lastActionTimestamp = Instant.now().getEpochSecond();
    public int getIdleTimeout() { return idleTimeout; }
    public void setIdleTimeout(int t) { idleTimeout = t; }
    public long getLastAction() { return lastActionTimestamp; }
    public void action() { lastActionTimestamp = Instant.now().getEpochSecond(); }
    public boolean timedOut() {
        return Instant.now().getEpochSecond() > (lastActionTimestamp + idleTimeout);
    }
}
