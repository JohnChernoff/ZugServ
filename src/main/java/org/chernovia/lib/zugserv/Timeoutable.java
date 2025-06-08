package org.chernovia.lib.zugserv;

import java.time.Instant;

/**
 * This Class contains methods for determining if an Object has "timed out", i.e.,
 * exceeded its alotted time since its last "action" (measured in seconds).
 */
public abstract class Timeoutable { //TODO: make interface?
    public enum ActionType {creation,phase,join,part,start,occupant,obs,nudge,other,user}
    private int idleTimeout = Integer.MAX_VALUE;
    private long lastActionTimestamp = Instant.now().getEpochSecond();
    final public int getIdleTimeout() { return idleTimeout; }
    final public void setIdleTimeout(int t) { idleTimeout = t; }
    final public long getLastAction() { return lastActionTimestamp; }
    public void action(Enum<?> type) { lastActionTimestamp = Instant.now().getEpochSecond(); }
    public boolean timedOut() {
        return Instant.now().getEpochSecond() > (lastActionTimestamp + idleTimeout);
    }
}
