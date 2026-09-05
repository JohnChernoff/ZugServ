package org.chernovia.lib.zugserv;

/**
 * An AreaListener responds to events relating to a ZugArea.
 */
public interface AreaListener<O extends Occupant, A extends ZugArea<O>> {

    /**
     * Called upon the (server defined) conclusion of an Area's purpose.
     * @param area the no longeer extant Area
     */
    void areaClosed(A area);

    void areaStarted(A area);

    void areaFinished(A area);

    /**
     * Called upon the creation of an Area.
     * @param area the newly created Area
     */
    void areaCreated(A area);

    /**
     * Called whenever the server considers an Area to have notably changed.
     * @param area the recently altered Area
     */
    void areaUpdated(A area, String updateType);

    void areaParted(A area, ZugUser user);

    void areaJoined(A area, O occupant);
}
