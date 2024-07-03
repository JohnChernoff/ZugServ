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
abstract public class ZugRoom extends Timeoutable implements JSONifier {

    private String title;

    private int maxOccupants = 99;

    private boolean isPrivate = false;

    private final ConcurrentHashMap<ZugUser.UniqueName,Occupant> occupants = new ConcurrentHashMap<>();

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
        if (occupant.isClone()) return false;
        if (occupants.putIfAbsent(occupant.getUser().getUniqueName(),occupant) == null) {
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
    public Optional<Occupant> dropOccupant(Occupant occupant) { //occupant.setArea(null);
        return dropOccupant(occupant.getUser());
    }

    /**
     * Drops an Occupant from the room.
     * @param user the ZugUser associated with the departing Occupant
     * @return The departed Occupant, if successful
     */
    public Optional<Occupant> dropOccupant(ZugUser user) {
        return Optional.ofNullable(occupants.remove(user.getUniqueName()));
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
     * @return a collection of Occupants
     */
    public Collection<Occupant> getActiveOccupants() {
        return getOccupants().stream().filter(occupant -> occupant.getUser().isLoggedIn()).toList();
    }

    public Optional<Occupant> getOccupant(ZugUser user) {
        return getOccupant(user.getUniqueName());
    }
    public Optional<Occupant> getOccupant(ZugUser.UniqueName name) {
        return Optional.ofNullable(occupants.get(name));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

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
                    occupant.tell(type,msg);
                }
            }
            else if (!occupant.isAway()) occupant.tell(type,msg);
        }
    }

    /**
     * Sends a JSON-encoded message and enumerated type to all unexcluded Occupants to the room.
     * @param type an enumerated type
     * @param msgNode a JSON-encoded message
     * @param exclude a list of excluded Occupants
     */
    public void spamX(Enum<?> type, ObjectNode msgNode, Occupant... exclude) {
        occupants.values().forEach(occupant -> {
            if (exclude != null) { //System.out.println("Checking ignore list");
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    occupant.tell(type, msgNode);
                }
            } else if (!occupant.isAway()) occupant.tell(type, msgNode);
        });
    }

    /**
     * Sends a JSON serialization of the room (toJSON()) to a specific ZugUser.
     * @param user a ZugUser (who may or may not be an Occupant of the room)
     */
    public void update(ZugUser user) {
        user.tell(ZugFields.ServMsgType.updateArea,toJSON());
    }

    /**
     * Sends a JSON serialization of the room (toJSON()) to each Occupant.
     */
    public void updateAll() {
        spam(ZugFields.ServMsgType.updateArea,toJSON());
    }

    /**
     * Sends a JSON serialization of the room's Occupant list (toJSON()) to each Occupant.
     */
    public void updateOccupants(boolean userOnly) { //System.out.println("Updating occupants");
        spam(ZugFields.ServMsgType.updateOccupants,occupantsToJSON(userOnly));
    }

    /**
     * Sends an alphanumeric message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric message
     */
    public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.roomMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
    }

    /**
     * Sends an alphanumeric error message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric error message
     */
    public void err(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.errMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
    }

    /**
     * Returns a JSON serialization of the room and its Occupants.
     * @return the result of toJSON(false)
     */
    public final ObjectNode toJSON() { return toJSON(false); }

    /**
     * Returns a JSON serialization of the room and optionally all Occupants. Subclasses may ovveride this.
     * @param listDataOnly if true, this does not include a list of Occupants
     * @return a JSON serialization of the room
     */
    public ObjectNode toJSON(boolean listDataOnly) {
        if (!listDataOnly) return occupantsToJSON(true);
        return ZugUtils.newJSON().put(ZugFields.TITLE,title);
    }

    /**
     * Returns JSON-formatted representation of all current Occupants of the room.
     * @param userOnly if true, this only lists Occupant user information
     * @return the JSON-formatted list
     */
    public ObjectNode occupantsToJSON(boolean userOnly) {
        ObjectNode node = ZugUtils.newJSON();
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(userOnly)));
        node.set(ZugFields.OCCUPANTS,arrayNode);
        node.put(ZugFields.TITLE,title);
        return node;
    }

}
