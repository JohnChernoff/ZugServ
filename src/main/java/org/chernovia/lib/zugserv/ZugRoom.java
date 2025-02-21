package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ZugRoom represents an area that can contain and rudimentarily manage an arbitrarily defined number of Occupants.
 */
abstract public class ZugRoom extends Timeoutable implements Comparable<ZugRoom>, JSONifier {

    private final String title;

    private int maxOccupants = 99;

    private boolean isPrivate = false;

    private final ConcurrentHashMap<String,Occupant> occupants = new ConcurrentHashMap<>();

    public ZugRoom(String title) {
        this.title = title;
    }

    /**
     * Indicates if a given Connection exists as an occupant of the room.
     * @param conn a Connection
     * @return true if in the room
     */
    public boolean isOccupant(Connection conn) {
        for (Occupant occupant : getOccupants()) {
            Connection c = occupant.getUser().getConn();
            if (c != null && (c.equals(conn) || c.isSameOrigin(conn))) return true;
        }
        return false;
    }

    /**
     * indicates if a given Connection or any other Connection with the same address exists as an occupant of the room.
     * @param conn a Connection
     * @param byOrigin if true, checks for other Connections with the same address
     * @return true if in the room
     */
    public boolean isOccupant(Connection conn, boolean byOrigin) {
        for (Occupant occupant : getOccupants()) {
            Connection c = occupant.getUser().getConn();
            if (c != null) {
                if (byOrigin) {
                    if (c.isSameOrigin(conn)) return true;
                }
                else if (c.equals(conn)) return true;
            }
        }
        return false;
    }

    /**
     * Adds an Occupant to this room. Notifies other occupants upon success.
     * @param occupant The Occupant to add
     * @return true upon success
     */
    public boolean addOccupant(Occupant occupant) {
        if (occupants.putIfAbsent(occupant.getUser().getUniqueName().toString(),occupant) == null) {
            action();
            updateOccupants(true); return true;
        }
        return false;
    }

    /**
     * Notifies an Occupant when they rejoin a room.  Many servers may wish to override this method.
     * @param occupant the rejoining Occupant
     */
    public void rejoin(Occupant occupant) {
        err(occupant.getUser(), "Already joined");
    }

    /**
     * Drops an Occupant from the room.
     * @param occupant the departing Occupant
     * @return The departed Occupant, if successful
     */
    public boolean dropOccupant(Occupant occupant) { //occupant.setArea(null);
        return dropOccupant(occupant.getUser());
    }

    /**
     * Drops an Occupant from the room.
     * @param user the ZugUser associated with the departing Occupant
     * @return The departed Occupant, if successful
     */
    public boolean dropOccupant(ZugUser user) {
        if (occupants.remove(user.getUniqueName().toString()) != null) {
            updateOccupants(true);
            return true;
        }
        return false;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public int getMaxOccupants() {
        return maxOccupants;
    }

    public void setMaxOccupants(int maxOccupants) {
        this.maxOccupants = maxOccupants;
    }

    public int numOccupants() {
        return occupants.size();
    }

    public Collection<Occupant> getOccupants() {
        return occupants.values();
    }

    /**
     * Returns a collection of currently logged in Occupants
     *
     * @param countAway counts away users as inactive
     * @return a collection of Occupants
     */
    public Collection<Occupant> getActiveOccupants(boolean countAway) {
        return getOccupants().stream().filter(occupant ->
                (countAway ? !occupant.isAway() :  occupant.getUser().isLoggedIn()) &&
                !occupant.isBot()).toList();
    }

    public Optional<Occupant> getOccupant(ZugUser user) {
        return getOccupant(user.getUniqueName());
    }

    public Optional<Occupant> getOccupant(ZugUser.UniqueName name) {
        return Optional.ofNullable(occupants.get(name.toString()));
    }

    public String getTitle() {
        return title;
    }

    abstract public String getName();

    /**
     * Sends a String message to all Occupants of the room.
     * @param msg an alphanumeric message
     */
    public void spam(String msg) {
        spam(ZugFields.ServMsgType.areaMsg,msg);
    }

    /**
     * Sends an enumerated type with a blank message to all Occupants of the room.
     * @param type the enumerated type
     */
    public void spam(Enum<?> type) {
        spam(type,"");
    }

    /**
     * Sends an alphanumeric message and enumerated type to all Occupants to the room.
     * @param type an enumerated type
     * @param msg an alphanumeric message
     */
    public void spam(Enum<?> type, String msg) {
        spamX(type,msg,  null);
    }

    /**
     * Sends a JSON-encoded message and enumerated type to all Occupants to the room.
     * @param type an enumerated type
     * @param msgNode a JSON-encoded message
     */
    public void spam(Enum<?> type, ObjectNode msgNode) { //ZugManager.log("Spamming: " + type + "," + msgNode.toString());
        spamX(type,msgNode, null);
    }

    /**
     * Sends an alphanumeric message and enumerated type to all unexcluded Occupants to the room.
     * @param type an enumerated type
     * @param msg an alphanumeric message
     * @param exclude a list of excluded Occupants
     */
    public void spamX(Enum<?> type, String msg, Occupant... exclude) {
        for (Occupant occupant : occupants.values()) {
            if (exclude != null) {
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    tell(occupant,type,msg);
                }
            }
            else if (!occupant.isAway()) tell(occupant,type,msg);
        }
    }

    /**
     * Sends a JSON-encoded message and enumerated type to all unexcluded Occupants to the room.
     * (Currently calls spamX(type,msgNode,false,exclude))
     * @param type an enumerated type
     * @param msgNode a JSON-encoded message
     * @param exclude a list of excluded Occupants
     */
    public void spamX(Enum<?> type, ObjectNode msgNode, Occupant... exclude) {
        spamX(type,msgNode,false,exclude);
    }

    /**
     * Sends a JSON-encoded message and enumerated type to all unexcluded Occupants to the room.
     * @param type an enumerated type
     * @param msgNode a JSON-encoded message
     * @param ignoreDeafness if true, ignores isDeafened()
     * @param exclude a list of excluded Occupants
     */
    public void spamX(Enum<?> type, ObjectNode msgNode, boolean ignoreDeafness, Occupant... exclude) {
        occupants.values().forEach(occupant -> {
            if (exclude != null) { //System.out.println("Checking ignore list");
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    tell(occupant,type, msgNode,ignoreDeafness);
                }
            } else if (!occupant.isAway()) tell(occupant,type, msgNode,ignoreDeafness);
        });
    }

    /**
     * Sends a JSON serialization of the room (updateToJSON()) to a specific ZugUser.
     * @param user a ZugUser (who may or may not be an Occupant of the room)
     */
    final public void update(ZugUser user) {
        user.tell(ZugFields.ServMsgType.updateArea,updateToJSON());
    }

    /**
     * Sends a JSON serialization of the room (updateToJSON()) to each Occupant.
     */
    final public void updateAll() {
        spam(ZugFields.ServMsgType.updateArea,updateToJSON());
    }

    /**
     * Sends a JSON serialization to a client/user. Can (and probably should) be overriden.
     * @return the (JSON formatted) data
     */
    public ObjectNode updateToJSON() {
        return toJSON(true);
    }

    /**
     * Sends a JSON serialization of the room's Occupant list (toJSON()) to each Occupant.
     */
    public void updateOccupants(boolean showRoom) { //System.out.println("Updating occupants");
        spam(ZugFields.ServMsgType.updateOccupants,occupantsToJSON(showRoom));
    }


    protected String getScope() {
        return ZugFields.ROOM_ID;
    }

    /**
     * Sends an alphanumeric message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric message
     */
    public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.roomMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(getScope(),getTitle()));
    }

    /**
     * Sends an alphanumeric error message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric error message
     */
    public void err(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.errMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(getScope(),getTitle()));
    }

    /**
     * Sends a blank message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     */
    public void tell(Occupant occupant, Enum<?> type) {
        tell(occupant,type,"");
    }

    /**
     * Sends a message with the default type (ZugFields.ServMsgType.servMsg).
     * @param occupant the message recipient
     * @param msg an alphanumeric message
     */
    public void tell(Occupant occupant, String msg) {
        tell(occupant,ZugFields.ServMsgType.servMsg,msg);
    }

    /**
     * Sends a message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param msg an alphanumeric message
     */
    public void tell(Occupant occupant, Enum<?> type, String msg) {
        tell(occupant, type,msg.isBlank() ? ZugUtils.newJSON() : ZugUtils.newJSON().put(ZugFields.MSG,msg));
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     */
    public void tell(Occupant occupant, Enum<?> type, ObjectNode node) {
        tell(occupant,type,node,false);
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     * @param ignoreDeafness it true, message is sent regardless of isDeafened()
     */
    public void tell(Occupant occupant, Enum<?> type, ObjectNode node,boolean ignoreDeafness) {
        if (!occupant.isDeafened() || ignoreDeafness) occupant.getUser().tell(type, node.put(getScope(),getTitle()));
    }

    /**
     * Returns a JSON serialization of the room (but not its Occupants).
     * @return the result of toJSON(false)
     */
    public final ObjectNode toJSON() { return toJSON(false); }

    /**
     * Returns a JSON serialization of the room and optionally all Occupants. Subclasses may ovveride this.
     * @param showOccupants if false, this does not include a list of Occupants
     * @return a JSON serialization of the room
     */
    public ObjectNode toJSON(boolean showOccupants) {
        if (showOccupants) return occupantsToJSON(false);
        return ZugUtils.newJSON().put(getScope(),title).put(ZugFields.NAME,getName());
    }

    /**
     * Returns JSON-formatted representation of all current Occupants of the room.
     * @param showRoom if true, this includes ZugRoom/ZugArea information
     * @return the JSON-formatted list
     */
    public ObjectNode occupantsToJSON(boolean showRoom) {
        ObjectNode node = ZugUtils.newJSON();
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(showRoom ? this : null)));
        node.set(ZugFields.OCCUPANTS,arrayNode);
        node.put(getScope(),title);
        return node;
    }

    @Override
    public int compareTo(ZugRoom a) {
        return this.getOccupants().size() - a.getOccupants().size();
    }

}
