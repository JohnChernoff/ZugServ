package org.chernovia.lib.zugserv;

abstract public class ServAdapter implements ZugServ {
    private int maxConnections = 127;
    protected final static boolean STACK_TRACE = true;
    private final ConnListener connListener;
    boolean running = false; boolean paused = false;

    public ServAdapter(final ConnListener connListener) {
        this.connListener = connListener;
    }

    /**
     * Gets the connection listener associated with this server.  The listener is responsible for the handling of messaages from Connections.
     * @return the connection listener.
     */
    @Override
    public ConnListener getConnListener() { return connListener; }

    @Override
    public void setPause(boolean paused) { this.paused = paused; }

    @Override
    public boolean isPaused() { return paused; }

    protected void setRunning(final boolean running) { this.running = running; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getMaxConnections() { return maxConnections; }

    @Override
    public void setMaxConnections(int c) {
        maxConnections = c;
    }


}
