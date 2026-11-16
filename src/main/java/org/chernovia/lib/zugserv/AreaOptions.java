package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

public class AreaOptions implements JSONifier, Comparable<AreaOptions> {

    final ObjectNode json;

    public AreaOptions() {
        json = ZugUtils.newJSON();
    }

    public AreaOptions(ObjectNode json) {
        this.json = json;
    }

    public boolean eq(AreaOptions other) {
        return json.equals(other.json);
    }

    @Override
    public int compareTo(@NotNull AreaOptions o) {
        return 0;
    }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        return json;
    }
}
