package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An Occupant encapsulates a ZugUser within a ZugArea.
 */
abstract public class Occupant implements JSONifier {

    private ZugUser user;
    public ZugUser getUser() { return user; }
    public void setUser(ZugUser user) { this.user = user; }
    private final ZugArea area;
    private ZugRoom room = null;
    private boolean isClone = false;
    private boolean muted = false;
    private boolean away = false;

    /**
     * Gets the ZugArea the Occupant is currently in.
     * @return the occupied ZugArea
     */
    public ZugArea getArea() { return area; }
    public ZugRoom getRoom() { return room; }
    public void joinRoom(ZugRoom r) {
        if (r.addOccupant(this)) {
            room = r;
            getUser().tell(ZugFields.ServMsgType.joinRoom,r.toJSON());
        }
    }
    public void clearRoom() {
        room = null; //TODO: send JSON?
    }

    public String getName() { return getName(true); }
    public String getName(boolean smart) {
        if (smart && area != null && area.getOccupants()
                .stream()
                .anyMatch(occupant -> occupant.user.getName().equalsIgnoreCase(user.getName())
                && occupant.user.getSource() != user.getSource())) return user.getUniqueName().toString();
        else return user.getName();
    }

    public boolean isClone() { return isClone; }
    public void setClone(boolean clone) { isClone = clone; }
    public boolean isAway() { return !user.isLoggedIn() || away; }
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
        area = a;
        if (area != null) {
            if (area.getOccupant(u).isPresent()) isClone = true;
            else if (area.addOccupant(this)) {
                getUser().tell(ZugFields.ServMsgType.joinArea,area.toJSON());
            }
        }
        if (!isClone && r != null) {
            joinRoom(r);
        }
    }

    public void tell(Enum<?> e) {
        tell(e,"");
    }

    public void tell(String msg) {
        tell(ZugFields.ServMsgType.servMsg,msg);
    }

    public void tell(Enum<?> e, String msg) {
        ObjectNode node = msg.isBlank() ? ZugUtils.newJSON() : ZugUtils.newJSON().put(ZugFields.MSG,msg);
        if (area != null) node.put(ZugFields.TITLE,area.title);
        if (room != null) node.put(ZugFields.ROOM,room.title);
        getUser().tell(e,node);
    }

    public void tell(Enum<?> e, ObjectNode node) {
        if (!isMuted()) getUser().tell(e,(ZugUtils.joinNodes(
                node,
                area != null ? ZugUtils.newJSON().put(ZugFields.TITLE,area.title) : null,
                room != null ? ZugUtils.newJSON().put(ZugFields.ROOM,room.title) : null
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
        ObjectNode node = ZugUtils.newJSON();
        node.put("away",away);
        node.put("banned",getArea().isBanned(getUser()));
        if (!userOnly) {
            node.set(ZugFields.AREA,area != null ? area.toJSON() : null);
            node.set(ZugFields.ROOM,room != null ? room.toJSON() : null);
        }
        node.set(ZugFields.USER,user.toJSON());
        return node;
    }

}
