package org.chernovia.lib.zugserv;

import java.util.*;
import java.lang.Math;
import java.util.concurrent.ConcurrentHashMap;

public class SeekManager {
    int threshCeiling = 100;
    int threshFloor = 0;
    int threshold = threshFloor;
    Map<ZugUser,ZugSeek> seekMap = new ConcurrentHashMap<>();
    ZugManager mgr;

    public SeekManager(ZugManager mgr) {
        this.mgr = mgr;
    }

    public void addSeek(ZugSeek seek) {
        seekMap.put(seek.user,seek);
        threshold = calcThreshold();
        seekMap.values().stream()
                .filter(s -> s != seek)
                .min(Comparator.comparingInt(s -> s.matchDiff(seek)))
                .ifPresent(bestMatch -> {
                    if (bestMatch.matchDiff(seek) <= threshold) matchSeeks(seek, bestMatch);
                });
    }

    protected int calcThreshold() {
        return Math.max(threshFloor,Math.min(threshCeiling, seekMap.size()));
    }

    public void matchSeeks(ZugSeek seek1, ZugSeek seek2) {
        seekMap.remove(seek1.user);
        seekMap.remove(seek2.user);
        mgr.handleCreateArea(List.of(seek1.user, seek2.user), null, true);
    }


}
