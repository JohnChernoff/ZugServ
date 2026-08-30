package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;

public class ZugSeek implements JSONifier {
    ZugUser user;
    long timeStamp;
    public ZugSeek(ZugUser user) {
        this.user = user;
        timeStamp = System.currentTimeMillis() / 1000;
    }

    /**
     * @implSpec This default implementation returns 2.5.
     * Subclasses should override as needed
     */
    public double patienceFactor() { return 2.5; }

    /**
     * @implSpec This default implementation returns the elo gap between
     * two {@link ZugPlayer}s (or 0 if either user isn't rated), minus a
     * discount based on combined wait time. Subclasses should override
     * to add other factors.
     */
    double matchDiff(ZugSeek other) {
        long now = System.currentTimeMillis() / 1000;
        int combinedAge = (int) ((now - timeStamp) + (now - other.timeStamp));

        double baseDiff = 0;
        if (user instanceof ZugPlayer p1 && other.user instanceof ZugPlayer p2) {
            baseDiff = Math.abs(p1.elo - p2.elo);
        }
        double patienceDiscount = combinedAge / patienceFactor();
        return Math.max(0, baseDiff - patienceDiscount);
    }

    /**
     * @implSpec This default implementation always returns {@code true}.
     * Subclasses should override to add compatibility constraints
     * (variant, options, seek type, etc.).
     */
    boolean isAcceptable(ZugSeek other) {
        return true; // default: any age-based diff is fine, always match oldest available
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.newJSON().set(ZugFields.USER,user.toJSON2(ZugScope.basic));
    }
}
