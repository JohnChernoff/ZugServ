package org.chernovia.lib.zugserv.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;

import java.util.Optional;

public class ZugServDemo extends ZugManager {

    public static void main(String[] args) {
        new ZugServDemo(ZugServ.ServType.WEBSOCK_JAVALIN,9999).getServ().startSrv();
    }

    public ZugServDemo(ZugServ.ServType type, int port) {
        super(type, port);
    }

    @Override
    public Optional<ZugUser> handleCreateUser(Connection conn, ZugUser.UniqueName name, JsonNode dataNode) {
        return Optional.of(new DemoUser(conn,name));

    }

    @Override
    public Optional<ZugArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode) {
        return Optional.of(new DemoArea(title,user,this));
    }

    @Override
    public Optional<Occupant> handleCreateOccupant(ZugUser user, ZugArea area, JsonNode dataNode) {
        return Optional.of(new DemoOccupant(user,area));
    }

    @Override
    public void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user) {

    }

    @Override
    public ObjectNode toJSON() {
        return super.toJSON();
    }

    class DemoUser extends ZugUser {
        public DemoUser(Connection c, UniqueName name) {
            super(c, name);
        }
    }

    class DemoOccupant extends Occupant {
        public DemoOccupant(ZugUser u, ZugArea a) {
            super(u, a);
        }
    }

    class DemoArea extends ZugArea {
        public DemoArea(String t, ZugUser c, AreaListener l) {
            super(t, c, l);
        }

        @Override
        public String getName() {
            return "DemoArea";
        }
    }
}
