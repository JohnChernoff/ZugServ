package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.OccupantFilter;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;

import java.util.*;

/**
 * An Occupant encapsulates a ZugUser within a ZugArea.
 */
abstract public class Occupant<O extends Occupant<O>> implements JSONifier {

    private ZugUser user;
    private boolean deafened = false;
    private boolean away = false;
    private final ZugArea<O> area;
    private ZugRoom<O> room;

    private final Map<String, Optional<Object>> responseMap = new HashMap<>();

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
                .anyMatch(occupant -> occupant.getUser().getName().equalsIgnoreCase(user.getName())
                && occupant.getUser().getSource() != user.getSource())) return user.getUniqueName().toString();
        else return user.getName();
    }

    /**
     * Indicates if the Occupant is whatever the ZugArea it occupies considers idle.
     * @return true if away, otherwise false
     */
    public boolean canAct() { return !user.isLoggedIn() || away; }

    public boolean isAway() { return away; }

    /**
     * Sets the away/idle status of an Occupant.
     * @param b true for away
     */
    public void setAway(boolean b) {
        away = b;
        if (away && area != null) area.handleAway((O)this);
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
    public Occupant(ZugUser u, ZugArea<O> area) {
        setUser(u);
        this.area = area;
    }

    public void setRoom(ZugRoom<O> room) {
        this.room = room;
    }
    public ZugRoom<O> getRoom() { return room; }

    public ZugArea<O> getArea() { return area; }

    /**
     * Determines if the Occupant has the name UniqueName as another.
     * @param o the Occupant to compare to
     * @return true if the same, otherwise false
     */
    public boolean eq(O o) {
        return user.getUniqueName().equals(o.getUser().getUniqueName());
    }

    /**
     * Serializes the Occupant to JSON. Subclasses should probably ovveride this.
     * @param scopeList which fields to serialize, ZugScopes.all for everything
     * @return a JSON serialization of the Occupant
     */
    @Override
    public ObjectNode toJSON2(Enum<?>... scopeList) {
        ObjectNode node = ZugUtils.newJSON();
        if (isBasic(scopeList)) {
            node.set(ZugFields.USER,user.toJSON());
            node.put("away",away);
            node.put("banned", area.isBanned(getUser()));
            node.put("creator", getArea().isCreator(getUser()));
        }
        return node;
    }

    public boolean isBot() {
        return getUser().getSource() == ZugAuthSource.bot;
    }

    public boolean passesFilter(OccupantFilter... filters) {
        List<OccupantFilter> filterList = Arrays.asList(filters);
        return
        (!filterList.contains(OccupantFilter.human) || !isBot()) &&
        (!filterList.contains(OccupantFilter.notAway) || !canAct()) &&
        (!filterList.contains(OccupantFilter.loggedIn) || user.isLoggedIn());
    }

    public Optional<Object> getResponse(String responseType) { return responseMap.get(responseType); }
    public void setResponse(String responseType, Object response) { //ZugManager.log("Got response: " + responseType + ", " + response);
        responseMap.put(responseType,Optional.ofNullable(response));
        if (response != null) area.rm().checkResponse(responseType);
    }

}
