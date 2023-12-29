package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

abstract public class Occupant {

    ZugUser user;
    public ZugUser getUser() { return user; }
    public void setUser(ZugUser user) { this.user = user; }
    ZugArea area;
    ZugRoom room;
    boolean isClone = false;
    public ZugArea getArea() { return area; }
    public boolean setArea(ZugArea a) {
        if (isClone || area == a) return false;
        if (area != null) area.dropOccupant(this);
        area = a; area.addOrGetOccupant(this);
        return true;
    }
    public ZugRoom getRoom() { return room; }
    public boolean setRoom(ZugRoom r) {
        if (isClone || room == r) return false;
        if (room != null) room.dropOccupant(this);
        room = r; room.addOrGetOccupant(this);
        return true;
    }

    public Occupant(ZugUser u, ZugArea a) {
        this(u,a,null);
    }

    public Occupant(ZugUser u, ZugArea a, ZugRoom r) {
        setUser(u);
        if (a != null && a.getOccupant(u) != null) isClone = true;
        else {
            setArea(a); setRoom(r);
        }
    }

    public void tell(Enum<?> e, String msg) {
        ObjectNode node = ZugManager.makeTxtNode(Map.entry(ZugFields.MSG,msg));
        if (area != null) node.put(ZugFields.TITLE,area.title);
        if (room != null) node.put(ZugFields.ROOM,room.title);
        getUser().tell(e,node);
    }

    public void tell(Enum<?> e, ObjectNode node) {
        getUser().tell(e,(ZugManager.joinNodes(
                node,
                area != null ? ZugManager.makeTxtNode(Map.entry(ZugFields.TITLE,area.title)) : null,
                room != null ? ZugManager.makeTxtNode(Map.entry(ZugFields.ROOM,room.title)) : null
        )));
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
