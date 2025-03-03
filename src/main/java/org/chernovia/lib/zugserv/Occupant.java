package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import org.chernovia.lib.zugserv.enums.ZugScope;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An Occupant encapsulates a ZugUser within a ZugArea.
 */
abstract public class Occupant implements JSONifier {

    //public enum ConfirmationChoice {yes,no,undecided}
    private ZugUser user;
    private boolean deafened = false;
    private boolean away = false;
    private final ZugArea area;
    private ZugRoom room;

    private final Map<String, Optional<Object>> objResponseMap = new HashMap<>();
    private final Map<String, Optional<Boolean>> boolResponseMap = new HashMap<>();
    private final Map<String, Optional<Integer>> intResponseMap = new HashMap<>();
    private final Map<String, Optional<Double>> doubleResponseMap = new HashMap<>();
    private final Map<String, Optional<String>> stringResponseMap = new HashMap<>();

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
     * If area != null, gets an Occupant's name when no other Occupant with the same same exists in the Area.
     * Otherwise, gets both Occupant username and source joined by the @ character.
     * @return a String representation of the Occupant's name and (optionally) source
     */
    public String getName() {
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
     * @param u the ZugUser associated with this Occupant
     */
    public Occupant(ZugUser u, ZugArea area) {
        setUser(u);
        this.area = area;
    }

    public void setRoom(ZugRoom room) {
        this.room = room;
    }
    public ZugRoom getRoom() { return room; }

    public ZugArea getArea() { return area; }

    /**
     * Determines if the Occupant has the name UniqueName as another.
     * @param o the Occupant to compare to
     * @return true if the same, otherwise false
     */
    public boolean eq(Occupant o) {
        return user.getUniqueName().equals(o.user.getUniqueName());
    }

    /**
     * Serializes the Occupant to JSON. Subclasses should probably ovveride this.
     * @param scopeList which fields to serialize, ZugScopes.all for everything
     * @return a JSON serialization of the Occupant
     */
    public ObjectNode toJSON(List<String> scopeList) {
        ObjectNode node = ZugUtils.newJSON();
        if (isBasic(scopeList)) {
            node.set(ZugFields.USER,user.toJSON());
            node.put("away",away);
            node.put("banned", area.isBanned(getUser()));
        }
        if (hasScope(scopeList, ZugScope.area,true)) {
            node.set(ZugFields.AREA,area.toJSON(ZugScope.basic));
        }
        if (hasScope(scopeList,ZugScope.room,true)) {
            node.set(ZugFields.ROOM,room.toJSON(ZugScope.basic));
        }
        return node;
    }

    public boolean isBot() {
        return getUser().getSource() == ZugAuthSource.bot;
    }

    public Optional<Object> getObjResponse(String confType) { return objResponseMap.get(confType); }
    public void setObjResponse(String confType, Object obj) {
        objResponseMap.put(confType,Optional.ofNullable(obj));
        area.checkObjResponse(confType);
    }

    public Optional<Boolean> getBoolResponse(String confType) { return boolResponseMap.get(confType); }
    public void setBoolResponse(String confType, Boolean bool) {
        boolResponseMap.put(confType,Optional.ofNullable(bool));
        area.checkBoolResponse(confType);
    }

    public Optional<Integer> getIntResponse(String confType) { return intResponseMap.get(confType); }
    public void setIntResponse(String confType, Integer val) {
        intResponseMap.put(confType,Optional.ofNullable(val));
        area.checkIntResponse(confType);
    }

    public Optional<Double> getDoubleResponse(String confType) { return doubleResponseMap.get(confType); }
    public void setDoubleResponse(String confType, Double val) {
        doubleResponseMap.put(confType,Optional.ofNullable(val));
        area.checkDoubleResponse(confType);
    }
    public Optional<String> getStringResponse(String confType) { return stringResponseMap.get(confType); }
    public void setStringResponse(String confType, String str) {
        stringResponseMap.put(confType,Optional.ofNullable(str));
        area.checkStringResponse(confType);
    }
}
