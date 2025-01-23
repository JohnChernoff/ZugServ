package org.chernovia.lib.zugserv;

public interface OccupantListener {
    public void handleAway(Occupant occupant);
    public void handleRoomJoin(Occupant occupant, ZugRoom prevRoom, ZugRoom newRoom);
}
