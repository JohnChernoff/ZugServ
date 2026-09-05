package org.chernovia.lib.zugserv;

public interface OccupantListener <T extends Occupant> {
    public void handleAway(T occupant);
    public void handleRoomJoin(T occupant, ZugRoom<T> prevRoom, ZugRoom<T> newRoom);

    //default void handleConfirmation(Occupant occupant, boolean confirm) { occupant.setConfirming(confirm); }
}
