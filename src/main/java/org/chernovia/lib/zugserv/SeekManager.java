package org.chernovia.lib.zugserv;

import org.chernovia.lib.zugserv.enums.ZugServMsgType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SeekManager {
    Map<ZugUser,ZugSeek> seekMap = new ConcurrentHashMap<>();
    ZugManager<?,?> mgr;

    public SeekManager(ZugManager<?,?> mgr) {
        this.mgr = mgr;
    }

    public void addSeek(ZugSeek seek) { //TODO: players > 2 seeks
        seekMap.put(seek.user, seek);
        seekMap.values().stream()
                .filter(s -> s != seek && s.isAcceptable(seek) && seek.isAcceptable(s))
                .min(Comparator.comparingDouble(s -> s.matchDiff(seek)))
                .ifPresent(bestMatch -> matchSeeks(bestMatch, seek));
    }

    public void matchSeeks(ZugSeek... seeks) {
        List<ZugUser> seekList = Arrays.stream(seeks).map(s -> s.user).toList();
        seekList.forEach(user -> {
            seekMap.remove(user);
            user.tell(ZugServMsgType.seekMatched);
        });
        mgr.handleCreateArea(seekList, null, true);
    }

}
