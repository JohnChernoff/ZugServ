package org.chernovia.lib.zugserv.tests;

import org.chernovia.lib.zugserv.AreaListener;
import org.chernovia.lib.zugserv.ZugArea;
import org.chernovia.lib.zugserv.ZugUser;

public class DemoArea extends ZugArea<DemoOccupant,DemoArea> {

    public DemoArea(String t, ZugUser c, AreaListener<DemoOccupant,DemoArea> l) {
        super(t, c, l);
    }

    @Override
    public String getName() {
        return "";
    }
}
