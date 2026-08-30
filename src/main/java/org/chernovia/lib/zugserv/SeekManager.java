package org.chernovia.lib.zugserv;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SeekManager {
    Map<ZugUser,ZugSeek> seekMap = new ConcurrentHashMap<>();
    ZugManager mgr;

    public SeekManager(ZugManager mgr) {
        this.mgr = mgr;
    }

    public void addSeek(ZugSeek seek) {
        seekMap.put(seek.user, seek);
        seekMap.values().stream()
                .filter(s -> s != seek && s.isAcceptable(seek) && seek.isAcceptable(s))
                .min(Comparator.comparingDouble(s -> s.matchDiff(seek)))
                .ifPresent(bestMatch -> matchSeeks(bestMatch, seek));
    }

    public void matchSeeks(ZugSeek seek1, ZugSeek seek2) {
        seekMap.remove(seek1.user);
        seekMap.remove(seek2.user);
        mgr.handleCreateArea(List.of(seek1.user, seek2.user), null, true);
    }

}
