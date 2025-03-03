package org.chernovia.lib.zugserv;

/**
 * An AreaListener responds to events relating to a ZugArea.
 */
public interface AreaListener {

    /**
     * Called upon the (server defined) conclusion of an Area's purpose.
     * @param area the no longeer extant Area
     */
    public void areaClosed(ZugArea area);

    public void areaStarted(ZugArea area);

    public void areaFinished(ZugArea area);

    /**
     * Called upon the creation of an Area.
     * @param area the newly created Area
     */
    public void areaCreated(ZugArea area);

    /**
     * Called whenever the server considers an Area to have notably changed.
     * @param area the recently altered Area
     */
    public void areaUpdated(ZugArea area);
}
