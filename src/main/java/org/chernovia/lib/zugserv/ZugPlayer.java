package org.chernovia.lib.zugserv;

public class ZugPlayer extends ZugUser {
    int elo;
    public ZugPlayer(Connection c, UniqueName uName, int elo) {
        super(c,uName);
        this.elo = elo;
    }
}
