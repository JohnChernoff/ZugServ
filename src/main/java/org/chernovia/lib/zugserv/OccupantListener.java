package org.chernovia.lib.zugserv;

public interface OccupantListener <O extends Occupant<O>> {
    public void handleAway(O occupant);
    public void handleRoomJoin(O occupant, ZugRoom<O> prevRoom, ZugRoom<O> newRoom);

    //default void handleConfirmation(Occupant occupant, boolean confirm) { occupant.setConfirming(confirm); }
}
