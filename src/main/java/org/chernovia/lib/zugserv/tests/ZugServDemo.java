package org.chernovia.lib.zugserv.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import java.util.List;
import java.util.Optional;

public class ZugServDemo extends ZugManager<DemoOccupant,DemoArea,ZugUser> {

    public static void main(String[] args) {
        new ZugServDemo().start();
    }

    public ZugServDemo() {
        super(ZugServ.ServType.WEBSOCK_JAVALIN, 2345, "ws", List.of("localhost"),null);
    }

    @Override
    public Optional<ZugUser> handleCreateUser(Connection conn, ZugUser.UniqueName name, JsonNode dataNode) {
        return Optional.of(new ZugUser(conn,name));

    }

    @Override
    public Optional<DemoArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode) {
        return Optional.of(new DemoArea(title,user,this));
    }

    @Override
    public Optional<DemoOccupant> handleCreateOccupant(ZugUser user, DemoArea area, JsonNode dataNode) {
        return Optional.of(new DemoOccupant(user, area));
    }

    @Override
    public void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user) {

    }

    @Override
    public ObjectNode toJSON() {
        return super.toJSON();
    }

}
