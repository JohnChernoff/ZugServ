package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

abstract public class Occupant implements JSONifier {

    ZugUser user;
    public ZugUser getUser() { return user; }
    public void setUser(ZugUser user) { this.user = user; }
    ZugArea area = null;
    ZugRoom room = null;
    boolean isClone = false;
    boolean muted = false;
    boolean away = false;
    public ZugArea getArea() { return area; }
    public boolean setArea(ZugArea a) {
        if (a == null || isClone || area == a) return false;
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

    public boolean isClone() { return isClone; }
    public void setClone(boolean clone) { isClone = clone; }
    public boolean isAway() { return away; }
    public void setAway(boolean b) { away = b; }
    public boolean isMuted() {
        return muted;
    }
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public Occupant(ZugUser u, ZugArea a) {
        this(u,a,null);
    }

    public Occupant(ZugUser u, ZugArea a, ZugRoom r) {
        setUser(u);
        if (a != null && a.getOccupant(u).isPresent()) isClone = true;
        else {
            if (a != null && setArea(a)) {
                //ZugManager.log("Creating Occupant: in " + a.title + ", occupants: " + a.occupants.values().size());
                a.updateOccupants();
            }
            if (r != null && setRoom(r)) r.updateOccupants();
        }
    }

    public void tell(Enum<?> e) {
        tell(e,"");
    }

    public void tell(String msg) {
        tell(ZugFields.ServMsgType.servMsg,msg);
    }

    public void tell(Enum<?> e, String msg) {
        ObjectNode node = msg.isBlank() ? ZugUtils.JSON_MAPPER.createObjectNode() : ZugUtils.makeTxtNode(Map.entry(ZugFields.MSG,msg));
        if (area != null) node.put(ZugFields.TITLE,area.title);
        if (room != null) node.put(ZugFields.ROOM,room.title);
        getUser().tell(e,node);
    }

    public void tell(Enum<?> e, ObjectNode node) {
        if (!isMuted()) getUser().tell(e,(ZugUtils.joinNodes(
                node,
                area != null ? ZugUtils.makeTxtNode(Map.entry(ZugFields.TITLE,area.title)) : null,
                room != null ? ZugUtils.makeTxtNode(Map.entry(ZugFields.ROOM,room.title)) : null
        )));
    }

    public void msg(String msg) {
        area.msg(user,msg);
    }

    public void err(String msg) {
        area.err(user,msg);
    }

    public boolean eq(Occupant o) {
        return user.getUniqueName().equals(o.user.getUniqueName());
    }

    public ObjectNode toJSON() { return toJSON(false); }
    public ObjectNode toJSON(boolean userOnly) {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        if (!userOnly) {
            node.set(ZugFields.AREA,area != null ? area.toJSON() : null);
            node.set(ZugFields.ROOM,room != null ? room.toJSON() : null);
        }
        node.set(ZugFields.USER,user.toJSON());
        return node;
    }

}
