package org.chernovia.lib.zugserv;

public interface OccupantListener <O extends Occupant<O>> {
    void handleAway(O occupant);
    void handleRoomJoin(O occupant, ZugRoom<O> prevRoom, ZugRoom<O> newRoom);

    //default void handleConfirmation(Occupant occupant, boolean confirm) { occupant.setConfirming(confirm); }
}
