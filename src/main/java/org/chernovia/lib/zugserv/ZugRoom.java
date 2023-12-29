package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Collection;
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

    public Occupant addOrGetOccupant(Occupant occupant) {
        return occupants.putIfAbsent(occupant.user.name,occupant);
    }

    public Occupant dropOccupant(Occupant occupant) {
        return dropOccupant(occupant.user);
    }
    public Occupant dropOccupant(ZugUser user) {
        return occupants.remove(user.getName());
    }

    public int numOccupants() {
        return occupants.size();
    }

    public Collection<Occupant> getOccupants() {
        return occupants.values();
    }

    public Occupant getOccupant(ZugUser user) {
        return getOccupant(user.name);
    }
    public Occupant getOccupant(String name) {
        return occupants.get(name);
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

    abstract public ObjectNode toJSON();

}
