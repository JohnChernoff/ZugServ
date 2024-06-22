package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

abstract public class ZugRoom extends Timeoutable implements JSONifier {

    String title;

    int maxOccupants = 99;

    boolean isPrivate = false;

    final ConcurrentHashMap<ZugUser.UniqueName,Occupant> occupants = new ConcurrentHashMap<>();

    public boolean addOccupant(Occupant occupant) { //TODO: should ZugManager call this?
        if (occupant.isClone()) return false;
        if (occupants.putIfAbsent(occupant.getUser().getUniqueName(),occupant) == null) {
            updateOccupants(); return true;
        }
        return false;
    }

    public void rejoin(Occupant occupant) {
        err(occupant.getUser(), "Already joined");
    }

    public Optional<Occupant> dropOccupant(Occupant occupant) { //occupant.setArea(null);
        return dropOccupant(occupant.getUser());
    }
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

    public void spam(String msg) {
        spam(ZugFields.ServMsgType.areaMsg,msg);
    }

    //public void spam(Enum<?> t, Occupant... exclude) { spam(t,"",exclude); }

    public void spam(Enum<?> t) {
        spam(t,"");
    }

    public void spam(Enum<?> t, String msg) {
        spamX(t,msg,  null);
    }

    public void spam(Enum<?> t, ObjectNode msgNode) {
        spamX(t,msgNode, null);
    }

    public void spamX(Enum<?> t, String msg, Occupant... exclude) {
        for (Occupant occupant : occupants.values()) {
            if (exclude != null) {
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    occupant.tell(t,msg);
                }
            }
            else if (!occupant.isAway()) occupant.tell(t,msg);
        }
    }

    public void spamX(Enum<?> t, ObjectNode msgNode, Occupant... exclude) {
        occupants.values().forEach(occupant -> {
            if (exclude != null) { //System.out.println("Checking ignore list");
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    occupant.tell(t, msgNode);
                }
            } else if (!occupant.isAway()) occupant.tell(t, msgNode);
        });
    }

    public void update(ZugUser user) {
        user.tell(ZugFields.ServMsgType.updateArea,toJSON());
    }

    public void updateAll() {
        spam(ZugFields.ServMsgType.updateArea,toJSON());
    }

    public void updateOccupants() { //System.out.println("Updating occupants");
        spam(ZugFields.ServMsgType.updateOccupants,occupantsToJSON());
    }

    public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.roomMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
    }
    public void err(ZugUser user, String msg) {
        user.tell(ZugFields.ServMsgType.errMsg,
                ZugUtils.newJSON().put(ZugFields.MSG,msg).put(ZugFields.TITLE,getTitle()));
    }

    public ObjectNode toJSON() { return toJSON(false); }
    public ObjectNode toJSON(boolean listDataOnly) {
        if (!listDataOnly) return occupantsToJSON();
        return ZugUtils.newJSON().put(ZugFields.TITLE,title);
    }
    public ObjectNode occupantsToJSON() {
        ObjectNode node = ZugUtils.newJSON();
        ArrayNode arrayNode = ZugUtils.newJSONArray();
        getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(true)));
        node.set(ZugFields.OCCUPANTS,arrayNode);
        node.put(ZugFields.TITLE,title);
        return node;
    }

}
