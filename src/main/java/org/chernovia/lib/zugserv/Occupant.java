package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

/**
 * An Occupant encapsulates a ZugUser within a ZugArea.
 */
abstract public class Occupant implements JSONifier {

    private ZugUser user;
    private final ZugArea area;
    private ZugRoom room = null;
    private boolean isClone = false;
    private boolean deafened = false;
    private boolean away = false;

    /**
     * Gets the ZugUser associated with this Occupant.
     * @return this Occupant's ZugUser
     */
    public ZugUser getUser() { return user; }

    /**
     * Gets the ZugUser associated with this Occupant.
     * @param user the associated ZugUser
     */
    public void setUser(ZugUser user) { this.user = user; }

    /**
     * Gets the ZugArea the Occupant is currently in.
     * @return the occupied ZugArea
     */
    public Optional<ZugArea> getArea() { return Optional.of(area); }

    /**
     * Gets the ZugRoom (if any) the Occupant is currently in.
     * @return the occupied ZugRoom, if any, null if none
     */
    public Optional<ZugRoom> getRoom() { return Optional.of(room); }

    /**
     * Attemps to joins a ZugRoom.
     * @param r The room to join
     * @return true if successful
     */
    public boolean joinRoom(ZugRoom r) {
        if (r.addOccupant(this)) {
            room = r;
            getUser().tell(ZugFields.ServMsgType.joinRoom,r.toJSON());
            return true;
        }
        return false;
    }

    /**
     * Leaves a ZugRoom.
     */
    public void partRoom() {
        room = null; //TODO: send JSON?
    }

    /**
     * Gets an Occupant's name.
     * If there are no other occupants with the same name, returns just the Occupant's name and no source.
     * Otherwise, returns the Occupant's username and the source joined by the @ character.
     * @return a String representation of the Occupant's name and (optionally) source
     */
    public String getName() { return getName(true); }

    /**
     * Gets an Occupant's name. With smart set to true, works like getName().
     * Otherwise, gets both Occupant username and source joined by the @ character.
     * @param smart get Occupant name only when no other Occupant with the same same exists in the Area
     * @return a String representation of the Occupant's name and (optionally) source
     */
    public String getName(boolean smart) {
        if (smart && area != null && area.getOccupants()
                .stream()
                .anyMatch(occupant -> occupant.user.getName().equalsIgnoreCase(user.getName())
                && occupant.user.getSource() != user.getSource())) return user.getUniqueName().toString();
        else return user.getName();
    }

    /**
     * Indicates if the Occupant is a copy of an existing Occupant in the same ZugArea.
     * @return true if a clone of a pre-existing Occupant, otherwise false
     */
    public boolean isClone() { return isClone; }

    /**
     * Indicates if the Occupant is whatever the ZugArea it occupies considers idle.
     * @return true if away, otherwise false
     */
    public boolean isAway() { return !user.isLoggedIn() || away; }

    /**
     * Sets the away/idle status of an Occupant.
     * @param b true for away
     */
    public void setAway(boolean b) { away = b; }

    /**
     * Indicates if an Occupant can receive tells.
     * @return true if deafened
     */
    public boolean isDeafened() {
        return deafened;
    }

    /**
     * Sets whether an Occupant may receive tells.
     * @param deafened true to deafen
     */
    public void setDeafened(boolean deafened) {
        this.deafened = deafened;
    }

    /**
     * Creates an Occupant and attempts to join an Area.
     * Upon success, sends a notification to the user and an update to the ZugArea.
     * @param u the ZugUser associated with this Occupant
     * @param a the ZugArea to occupy
     */
    public Occupant(ZugUser u, ZugArea a) {
        this(u,a,null);
    }

    /**
     * Creates an Occupant and attempts to join an Area.
     * Upon success, sends a notification to the user and an update to the ZugArea and/or ZugRoom.
     * @param u the ZugUser associated with this Occupant
     * @param a the ZugArea to occupy
     * @param r the ZugRoom to join
     */
    public Occupant(ZugUser u, ZugArea a, ZugRoom r) {
        setUser(u);
        area = a;
        if (area != null) {
            if (area.getOccupant(u).isPresent()) isClone = true;
            //else if (area.addOccupant(this)) { getUser().tell(ZugFields.ServMsgType.joinArea,area.toJSON()); }
        }
        if (!isClone && r != null) {
            joinRoom(r);
        }
    }

    /**
     * Sends a blank message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     */
    public void tell(Enum<?> type) {
        tell(type,"");
    }

    /**
     * Sends a message with the default type (ZugFields.ServMsgType.servMsg).
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param msg an alphanumeric message
     */
    public void tell(String msg) {
        tell(ZugFields.ServMsgType.servMsg,msg);
    }

    /**
     * Sends a message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param msg an alphanumeric message
     */
    public void tell(Enum<?> type, String msg) {
        tell(type,msg.isBlank() ? ZugUtils.newJSON() : ZugUtils.newJSON().put(ZugFields.MSG,msg));
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     */
    public void tell(Enum<?> type, ObjectNode node) {
        tell(type,node,false);
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     * @param ignoreDeafness it true, message is sent regardless of isDeafened()
     */
    public void tell(Enum<?> type, ObjectNode node,boolean ignoreDeafness) {
        if (!isDeafened() || ignoreDeafness) getUser().tell(type,(ZugUtils.joinNodes(
                node,
                area != null ? ZugUtils.newJSON().put(ZugFields.TITLE,area.getTitle()) : null,
                room != null ? ZugUtils.newJSON().put(ZugFields.ROOM,room.getTitle()) : null
        )));
    }

    /**
     * Serializes the Occupant (typcially via toJSON()) to a Connection.
     * @param conn the Connection to update
     */
    public void update(Connection conn) {
        if (conn != null) conn.tell(ZugFields.ServMsgType.updateOccupant,toJSON());
    }

    /**
     * Determines if the Occupant has the name UniqueName as another.
     * @param o the Occupant to compare to
     * @return true if the same, otherwise false
     */
    public boolean eq(Occupant o) {
        return user.getUniqueName().equals(o.user.getUniqueName());
    }

    /**
     * Serializes the Occupant to JSON.
     * @return the results of toJSON(false)
     */
    public final ObjectNode toJSON() { return toJSON(false); }

    /**
     * Serializes the Occupant to JSON. Subclasses should probably ovveride this.
     * @param showRoom includes ZugArea/ZugRoom information
     * @return a JSON serialization of the Occupant
     */
    public ObjectNode toJSON(boolean showRoom) {
        ObjectNode node = ZugUtils.newJSON();
        node.put("away",away);
        node.put("banned",area.isBanned(getUser()));
        if (showRoom) {
            node.set(ZugFields.AREA,area != null ? area.toJSON() : null); //TODO: how is area never null?
            node.set(ZugFields.ROOM,room != null ? room.toJSON() : null);
        }
        node.set(ZugFields.USER,user.toJSON());
        return node;
    }

}
