package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
            spam(ZugServMsgType.updateOccupants,toJSON(ZugScope.occupants_basic));
            return true;
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
            spam(ZugServMsgType.updateOccupants,toJSON(ZugScope.occupants_basic));
            return true;
        }
        return false;
    }

    public final boolean isPrivate() {
        return isPrivate;
    }

    public final void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public final int getMaxOccupants() {
        return maxOccupants;
    }

    public final void setMaxOccupants(int maxOccupants) {
        this.maxOccupants = maxOccupants;
    }

    public final int numOccupants() {
        return occupants.size();
    }

    public final Collection<Occupant> getOccupants() {
        return occupants.values();
    }

    /**
     * Returns a collection of currently logged in Occupants
     *
     * @param countAway counts away users as inactive
     * @return a collection of Occupants
     */
    public final Collection<Occupant> getActiveOccupants(boolean countAway) {
        return getOccupants().stream().filter(occupant ->
                (countAway ? !occupant.isAway() :  occupant.getUser().isLoggedIn()) &&
                !occupant.isBot()).toList();
    }

    public final Optional<Occupant> getOccupant(ZugUser user) {
        return getOccupant(user.getUniqueName());
    }

    public final Optional<Occupant> getOccupant(ZugUser.UniqueName name) {
        return Optional.ofNullable(occupants.get(name.toString()));
    }

    public final String getTitle() {
        return title;
    }

    abstract public String getName();

    /**
     * Sends a String message to all Occupants of the room.
     * @param msg an alphanumeric message
     */
    public final void spam(String msg) {
        spam(ZugServMsgType.areaMsg,msg);
    }

    /**
     * Sends an enumerated type with a blank message to all Occupants of the room.
     * @param type the enumerated type
     */
    public final void spam(Enum<?> type) {
        spam(type,"");
    }

    /**
     * Sends an alphanumeric message and enumerated type to all Occupants to the room.
     * @param type an enumerated type
     * @param msg an alphanumeric message
     */
    public final void spam(Enum<?> type, String msg) {
        spamX(type,msg,  null);
    }

    /**
     * Sends a JSON-encoded message and enumerated type to all Occupants to the room.
     * @param type an enumerated type
     * @param msgNode a JSON-encoded message
     */
    public final void spam(Enum<?> type, ObjectNode msgNode) { //ZugManager.log("Spamming: " + type + "," + msgNode.toString());
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
    public final void spamX(Enum<?> type, ObjectNode msgNode, boolean ignoreDeafness, Occupant... exclude) {
        occupants.values().forEach(occupant -> {
            if (exclude != null) { //System.out.println("Checking ignore list");
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    tell(occupant,type, msgNode,ignoreDeafness);
                }
            } else if (!occupant.isAway()) tell(occupant,type, msgNode,ignoreDeafness);
        });
    }

   /**
     * Sends an alphanumeric message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric message
     */
    public void msg(ZugUser user, String msg) {
        user.tell(ZugServMsgType.roomMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.AREA_ID,getTitle()));
    }

    /**
     * Sends an alphanumeric error message from the room to a ZugUser
     * @param user the ZugUser
     * @param msg the alphanumeric error message
     */
    public void err(ZugUser user, String msg) {
        user.tell(ZugServMsgType.errMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.AREA_ID,getTitle()));
    }

    /**
     * Sends a blank message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     */
    public final void tell(Occupant occupant, Enum<?> type) {
        tell(occupant,type,"");
    }

    /**
     * Sends a message with the default type (ZugFields.ServMsgType.servMsg).
     * @param occupant the message recipient
     * @param msg an alphanumeric message
     */
    public final void tell(Occupant occupant, String msg) {
        tell(occupant, ZugServMsgType.servMsg,msg);
    }

    /**
     * Sends a message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param msg an alphanumeric message
     */
    public final void tell(Occupant occupant, Enum<?> type, String msg) {
        tell(occupant, type,msg.isBlank() ? ZugUtils.newJSON() : ZugUtils.newJSON().put(ZugFields.MSG,msg));
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     */
    public final void tell(Occupant occupant, Enum<?> type, ObjectNode node) {
        tell(occupant,type,node,false);
    }

    /**
     * Sends a JSON-formatted message with the indicated type.
     * @param occupant the message recipient
     * @param type the enumerated message type
     * @param node a JSON-formatted message
     * @param ignoreDeafness it true, message is sent regardless of isDeafened()
     */
    public final void tell(Occupant occupant, Enum<?> type, ObjectNode node,boolean ignoreDeafness) {
        if (!occupant.isDeafened() || ignoreDeafness) occupant.getUser().tell(type, node.put(ZugFields.AREA_ID,getTitle()));
    }

    /**
     * Returns a JSON serialization of the room (but not its Occupants).
     * @return the result of toJSON(ZugFields.SCOPE_BASIC)
     */
    public final ObjectNode toJSON() { return toJSON(ZugScope.basic); }

    public ObjectNode toJSON(List<String> scopeList) {
        ObjectNode node = ZugUtils.newJSON();
        if (isBasic(scopeList)) {
            node.put(ZugFields.AREA_ID,title).put(ZugFields.NAME,getName());
        }
        //scope_all = occupants.toJson(ZugScope.all) (doesn't include room/area info)
        if (hasScope(scopeList,ZugScope.occupants_basic) || hasScope(scopeList,ZugScope.occupants_all)) {
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            if (hasScope(scopeList,ZugScope.occupants_basic,true)) {
                getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(ZugScope.basic)));
            }
            else {
                getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(ZugScope.all)));
            }
            node.set(ZugFields.OCCUPANTS,arrayNode);
        }
        return node;
    }

    @Override
    public int compareTo(ZugRoom a) {
        return this.getOccupants().size() - a.getOccupants().size();
    }

}
