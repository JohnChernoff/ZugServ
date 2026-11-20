package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.JsonNode;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SeekManager<U extends ZugUser> {
    public static final int MAX_CHALLENGES_PER_USER = 3;
    public static final long DEFAULT_CHALLENGE_TTL = 24L * 60 * 60 * 1000; //24 hours, in milliseconds
    private static final String ID_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int ID_LENGTH = 10; //62^10 is ~8e17: not guessable
    private static final SecureRandom RNG = new SecureRandom();

    Map<U,ZugSeek<U>> seekMap = new ConcurrentHashMap<>();
    /** Link-only seeks, keyed by challenge id. Deliberately separate from seekMap so the auto-matcher never sees them. */
    private final Map<String,ZugChallenge<U>> challengeMap = new ConcurrentHashMap<>();
    private volatile long challengeTTL = DEFAULT_CHALLENGE_TTL;
    ZugManager<?,?,U> mgr;

    public SeekManager(ZugManager<?,?,U> mgr) {
        this.mgr = mgr;
    }

    public void addSeek(ZugSeek<U> seek) { //TODO: players > 2 seeks
        seekMap.put(seek.user, seek);
        seekMap.values().stream()
                .filter(s -> s != seek && s.isAcceptable(seek) && seek.isAcceptable(s))
                .min(Comparator.comparingDouble(s -> s.matchDiff(seek)))
                .ifPresent(bestMatch -> matchSeeksWith(seek.options.toJSON(),bestMatch, seek));
    }

    /**
     * Matches the given seeks and creates an area for them.
     * @param data optional settings passed along as the dataNode for area/occupant creation (null for none)
     */
    @SafeVarargs
    public final void matchSeeksWith(JsonNode data, ZugSeek<U>... seeks) {
        List<U> seekList = Arrays.stream(seeks).map(s -> s.user).toList();
        seekList.forEach(user -> {
            seekMap.remove(user);
            user.tell(ZugServMsgType.seekMatched);
        });
        mgr.handleCreateAreaAndPopualate(seekList, data, true);
    }

    /* *** Challenges *** */

    public void setChallengeTTL(long millis) { challengeTTL = millis; }
    public long getChallengeTTL() { return challengeTTL; }

    /**
     * Registers a link-only seek.
     * @param seek the creator's seek (normally from {@link ZugManager#createSeek})
     * @param settings optional game-specific settings, retained for area creation if the challenge is accepted
     * @return the new challenge, or empty if the creator already has {@link #MAX_CHALLENGES_PER_USER} open
     */
    public synchronized Optional<ZugChallenge<U>> addChallenge(ZugSeek<U> seek, JsonNode settings) {
        long open = challengeMap.values().stream().filter(c -> c.getCreator() == seek.user && !c.isExpired()).count();
        if (open >= MAX_CHALLENGES_PER_USER) return Optional.empty();
        while (true) {
            ZugChallenge<U> challenge = new ZugChallenge<>(newChallengeID(), seek, settings, challengeTTL);
            if (challengeMap.putIfAbsent(challenge.getID(), challenge) == null) return Optional.of(challenge);
        }
    }

    private static String newChallengeID() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) sb.append(ID_CHARS.charAt(RNG.nextInt(ID_CHARS.length())));
        return sb.toString();
    }

    /** @return the challenge, unless it doesn't exist or has expired */
    public Optional<ZugChallenge<U>> getChallenge(String id) {
        ZugChallenge<U> challenge = id == null ? null : challengeMap.get(id);
        if (challenge != null && challenge.isExpired()) {
            challengeMap.remove(id, challenge);
            return Optional.empty();
        }
        return Optional.ofNullable(challenge);
    }

    /**
     * Atomically takes a challenge off the board, so that it can only ever be accepted once.
     * @return true if the caller got it, false if it was already claimed, cancelled or purged
     */
    public boolean claimChallenge(ZugChallenge<U> challenge) {
        return !challenge.isExpired() && challengeMap.remove(challenge.getID(), challenge);
    }

    /** @return true if the challenge existed and belonged to the given user */
    public boolean cancelChallenge(String id, U user) {
        ZugChallenge<U> challenge = id == null ? null : challengeMap.get(id);
        return challenge != null && challenge.getCreator() == user && challengeMap.remove(id, challenge);
    }

    public boolean cancelSeek(U user) {
        return seekMap.remove(user) != null;
    }

    /** Removes and returns all expired challenges, so the caller can notify their creators. */
    public List<ZugChallenge<U>> purgeExpired() {
        List<ZugChallenge<U>> purged = new ArrayList<>();
        for (ZugChallenge<U> challenge : challengeMap.values()) {
            if (challenge.isExpired() && challengeMap.remove(challenge.getID(), challenge)) purged.add(challenge);
        }
        return purged;
    }

    /** Forgets everything (seeks and challenges) belonging to a user, e.g. upon disconnection. */
    public void dropUser(U user) {
        seekMap.remove(user);
        challengeMap.values().removeIf(c -> c.getCreator() == user);
    }

}
