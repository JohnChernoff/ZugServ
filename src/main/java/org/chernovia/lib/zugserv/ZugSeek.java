package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;

public class ZugSeek implements JSONifier {
    ZugUser user;

    public ZugSeek(ZugUser user) {
        this.user = user;
    }

    int matchDiff(ZugSeek other) {
        return 0; // e.g. Math.abs(this.rating - other.rating)
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return ZugUtils.newJSON().set(ZugFields.USER,user.toJSON2(ZugScope.basic));
    }
}
