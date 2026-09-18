package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;

import java.util.List;

/**
 * A link-only seek. Unlike an ordinary {@link ZugSeek}, a challenge is never considered by the automatic
 * matcher: it can only be accepted by someone who knows its (unguessable) id, typically via a clickable link.
 * <p>
 * A challenge wraps whatever {@link ZugSeek} the game's {@link ZugManager#createSeek} produces, so any
 * game-specific compatibility rules carry over unchanged. Challenges are single-use and expire.
 */
public class ZugChallenge implements JSONifier {
    private final String id;
    private final ZugSeek seek;
    private final ObjectNode settings;
    private final long created, expires; //epoch millis

    /** Routing fields a client may not smuggle into area creation via challenge settings. */
    private static final List<String> RESERVED_FIELDS = List.of(ZugFields.AREA_ID, ZugFields.AREA_TITLE, ZugFields.AUTO_JOIN);

    ZugChallenge(String id, ZugSeek seek, JsonNode rawSettings, long ttlMillis) {
        this.id = id;
        this.seek = seek;
        this.settings = ZugUtils.newJSON();
        if (rawSettings != null && rawSettings.isObject()) settings.setAll(((ObjectNode) rawSettings).deepCopy());
        settings.remove(RESERVED_FIELDS);
        this.created = System.currentTimeMillis();
        this.expires = created + ttlMillis;
    }

    public String getID() { return id; }
    public ZugSeek getSeek() { return seek; }
    public ZugUser getCreator() { return seek.user; }
    public long getExpiration() { return expires; }
    public boolean isExpired() { return System.currentTimeMillis() >= expires; }

    /**
     * @return the creator's game-specific settings (never null), suitable as the dataNode
     * handed to {@link ZugManager#handleCreateArea} and {@link ZugManager#handleCreateOccupant}
     */
    public ObjectNode getSettings() { return settings; }

    @Override
    public ObjectNode toJSON2(Enum<?>... scopes) {
        ObjectNode node = ZugUtils.newJSON()
                .put(ZugFields.CHALLENGE_ID, id)
                .put(ZugFields.EXPIRES, expires);
        node.set(ZugFields.CREATOR, seek.user.getUniqueName().toJSON2(ZugScope.all));
        node.set(ZugFields.DATA, settings);
        return node;
    }
}
