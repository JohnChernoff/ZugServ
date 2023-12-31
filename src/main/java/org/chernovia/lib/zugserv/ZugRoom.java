package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

abstract public class ZugRoom {

    String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    final ConcurrentHashMap<String,Occupant> occupants = new ConcurrentHashMap<>();

    public Optional<Occupant> addOrGetOccupant(Occupant occupant) {
        return Optional.ofNullable(occupants.putIfAbsent(occupant.user.name,occupant));
    }

    public Optional<Occupant> dropOccupant(Occupant occupant) {
        return dropOccupant(occupant.user);
    }
    public Optional<Occupant> dropOccupant(ZugUser user) {
        return Optional.ofNullable(occupants.remove(user.getName()));
    }

    public int numOccupants() {
        return occupants.size();
    }

    public Collection<Occupant> getOccupants() {
        return occupants.values();
    }

    public Optional<Occupant> getOccupant(ZugUser user) {
        return getOccupant(user.name);
    }
    public Optional<Occupant> getOccupant(String name) {
        return Optional.ofNullable(occupants.get(name));
    }

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
            else occupant.tell(t,msg);
        }
    }

    public void spamX(Enum<?> t, ObjectNode msgNode, Occupant... exclude) {
        for (Occupant occupant : occupants.values()) {
            if (exclude != null) { //System.out.println("Checking ignore list");
                if (Arrays.stream(exclude).noneMatch(o -> o.equals(occupant))) {
                    occupant.tell(t,msgNode);
                }
            }
            else occupant.tell(t,msgNode);
        }
    }

    public void update(Occupant occupant) {
        occupant.tell(ZugFields.ServTypes.updateArea,toJSON(false));
    }

    public void updateAll() {
        System.out.println("Updating all");
        spam(ZugFields.ServTypes.updateArea,toJSON(false));
    }

    public void msg(ZugUser user, String msg) {
        user.tell(ZugFields.ServTypes.roomMsg,ZugUtils.makeTxtNode
                (Map.entry(ZugFields.MSG,msg),Map.entry(ZugFields.TITLE,getTitle())));
    }
    public void err(ZugUser user, String msg) {
        user.tell(ZugFields.ServTypes.errMsg,ZugUtils.makeTxtNode
                (Map.entry(ZugFields.MSG,msg),Map.entry(ZugFields.TITLE,getTitle())));
    }

    public ObjectNode toJSON() { return toJSON(false); }
    public ObjectNode toJSON(boolean titleOnly) {
        ObjectNode node = ZugUtils.JSON_MAPPER.createObjectNode();
        if (!titleOnly) {
            ArrayNode arrayNode = ZugUtils.JSON_MAPPER.createArrayNode();
            getOccupants().forEach(occupant -> arrayNode.add(occupant.toJSON(true)));
            node.set(ZugFields.OCCUPANTS,arrayNode);
        }
        node.put(ZugFields.TITLE,title);
        return node;
    }

}
