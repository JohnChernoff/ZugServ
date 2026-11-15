package org.chernovia.lib.zugserv.tests;

import org.chernovia.lib.zugserv.Occupant;
import org.chernovia.lib.zugserv.ZugArea;
import org.chernovia.lib.zugserv.ZugUser;

public class DemoOccupant extends Occupant<DemoOccupant> {

    /**
     * Creates a roomless non-bot Occupant - note that whatever creates this is responsible for adding it to its assigned Area.
     *
     * @param u    the ZugUser associated with this Occupant
     * @param area
     */
    public DemoOccupant(ZugUser u, ZugArea<DemoOccupant,DemoArea> area) {
        super(u, area);
    }
}
