package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

abstract public class Occupant {

    ZugUser user;
    public ZugUser getUser() { return user; }
    public void setUser(ZugUser user) { this.user = user; }

    ZugArea area;
    public ZugArea getArea() { return area; }
    public void setArea(ZugArea area) { this.area = area; }

    public Occupant(ZugUser u, ZugArea a) {
        user = u; area = a;
    }

    public void tell(Enum e, String msg) {
        ZugArea area = getArea();
        if (area != null) {
            getUser().tell(e,(ZugManager.makeTxtNode(
                    Map.entry(ZugServ.MSG,msg),
                    Map.entry(ZugServ.SOURCE,area.getTitle()))));
        }
    }

    public void tell(Enum e, ObjectNode node) {
        ZugArea area = getArea();
        if (area != null) {
            getUser().tell(e,(ZugManager.joinNodes(
                    node,
                    ZugManager.makeTxtNode(Map.entry(ZugServ.SOURCE,area.getTitle())))));
        }
    }

    public void msg(String msg) {
        area.msg(user,msg);
    }

    public void err(String msg) {
        area.err(user,msg);
    }

    public boolean eq(Occupant o) {
        return user.getName().equalsIgnoreCase(o.user.name);
    }

    abstract public ObjectNode toJSON();

}
