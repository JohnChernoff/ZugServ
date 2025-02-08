package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An Occupant encapsulates a ZugUser within a ZugArea.
 */
abstract public class Occupant implements JSONifier {

    private ZugUser user;
    private boolean deafened = false;
    private boolean isBot;
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
     * Gets an Occupant's name and source joined by the @ character.
     * @return a String representation of the Occupant's name and source
     */
    public String getName() { return getName(null); }

    /**
     * If area != null, gets an Occupant's name when no other Occupant with the same same exists in the Area.
     * Otherwise, gets both Occupant username and source joined by the @ character.
     * @param area the Occupant's current Area
     * @return a String representation of the Occupant's name and (optionally) source
     */
    public String getName(ZugArea area) {
        if (area != null && area.getOccupants()
                .stream()
                .anyMatch(occupant -> occupant.user.getName().equalsIgnoreCase(user.getName())
                && occupant.user.getSource() != user.getSource())) return user.getUniqueName().toString();
        else return user.getName();
    }

    /**
     * Indicates if the Occupant is whatever the ZugArea it occupies considers idle.
     * @return true if away, otherwise false
     */
    public boolean isAway() { return !user.isLoggedIn() || away; }

    /**
     * Sets the away/idle status of an Occupant.
     * @param b true for away
     */
    public void setAway(boolean b, ZugArea area) {
        away = b;
        if (away && area != null) area.handleAway(this);
    }

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
     * Creates a roomless non-bot Occupant - note that whatever creates this is responsible for adding it to its assigned Area.
     * Upon success, sends a notification to the user and an update to the ZugArea.
     * @param u the ZugUser associated with this Occupant
     */
    public Occupant(ZugUser u) {
        this(u,false);
    }

    /**
     * Creates an Occupant - note that whatever creates this is responsible for adding it to its assigned Area.
     * Upon success, sends a notification to the user and an update to the ZugArea and/or ZugRoom.
     * @param u the ZugUser associated with this Occupant
     * @param bot true if occupant is a "bot"
     */
    public Occupant(ZugUser u, boolean bot) {
        isBot = bot;
        setUser(u);
    }

    /**
     * Sends a blank message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     */
    public void tell(Enum<?> type, ZugRoom room) {
        tell(type,"",room);
    }

    /**
     * Sends a message with the default type (ZugFields.ServMsgType.servMsg).
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param msg an alphanumeric message
     */
    public void tell(String msg, ZugRoom room) {
        tell(ZugFields.ServMsgType.servMsg,msg,room);
    }

    /**
     * Sends a message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param msg an alphanumeric message
     */
    public void tell(Enum<?> type, String msg, ZugRoom room) {
        tell(type,msg.isBlank() ? ZugUtils.newJSON() : ZugUtils.newJSON().put(ZugFields.MSG,msg),room);
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     */
    public void tell(Enum<?> type, ObjectNode node, ZugRoom room) {
        tell(type,node,false, room);
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * The message will automatically include the ZugArea and ZugRoom titles.
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     * @param ignoreDeafness it true, message is sent regardless of isDeafened()
     */
    public void tell(Enum<?> type, ObjectNode node,boolean ignoreDeafness, ZugRoom room) {
        if (!isDeafened() || ignoreDeafness) getUser().tell(type,(ZugUtils.joinNodes(
                node,
                room instanceof ZugArea area
                        ? ZugUtils.newJSON().put(ZugFields.TITLE,area.getTitle())
                        : ZugUtils.newJSON().put(ZugFields.ROOM,room.getTitle())
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
     * @return the results of toJSON(null)
     */
    public final ObjectNode toJSON() { return toJSON(null); }

    /**
     * Serializes the Occupant to JSON. Subclasses should probably ovveride this.
     * @param room includes ZugArea/ZugRoom information
     * @return a JSON serialization of the Occupant
     */
    public ObjectNode toJSON(ZugRoom room) {
        ObjectNode node = ZugUtils.newJSON();
        node.put("away",away);
        node.put("banned", room instanceof ZugArea area && area.isBanned(getUser()));
        if (room != null) {
            if (room instanceof ZugArea area) node.set(ZugFields.AREA,area.toJSON());
            else node.set(ZugFields.ROOM,room.toJSON());
        }
        node.set(ZugFields.USER,user.toJSON());
        return node;
    }

    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        isBot = bot;
    }
}
