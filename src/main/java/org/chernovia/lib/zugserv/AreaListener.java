package org.chernovia.lib.zugserv;

public interface AreaListener {
    public void areaFinished(ZugArea area);
    public void areaCreated(ZugArea area);
    public void areaUpdated(ZugArea area);
}
