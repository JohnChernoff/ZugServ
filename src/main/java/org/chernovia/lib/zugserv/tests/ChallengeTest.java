// ============================================================================
// ZugServ link-only challenge tests - standalone (no server needed)
// ============================================================================
// Run as a normal Java application.

package org.chernovia.lib.zugserv.tests;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ChallengeTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int userCount = 0;

    public static void main(String[] args) {
        System.out.println("ZugServ Challenge Test Suite\n");

        test("ids are unguessable-looking, unique and retrievable", () -> {
            SeekManager sm = new SeekManager(null);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 200; i++) {
                ZugChallenge c = sm.addChallenge(new ZugSeek(newUser()), null).orElseThrow();
                assertEquals(10, c.getID().length(), "id length");
                assertTrue(ids.add(c.getID()), "duplicate id: " + c.getID());
                assertTrue(sm.getChallenge(c.getID()).isPresent(), "not retrievable");
            }
            assertFalse(sm.getChallenge("nope").isPresent(), "bogus id found");
            assertFalse(sm.getChallenge(null).isPresent(), "null id found");
        });

        test("per-user cap", () -> {
            SeekManager sm = new SeekManager(null);
            ZugUser user = newUser();
            for (int i = 0; i < SeekManager.MAX_CHALLENGES_PER_USER; i++) {
                assertTrue(sm.addChallenge(new ZugSeek(user), null).isPresent(), "should be allowed: " + i);
            }
            assertFalse(sm.addChallenge(new ZugSeek(user), null).isPresent(), "cap not enforced");
            assertTrue(sm.addChallenge(new ZugSeek(newUser()), null).isPresent(), "cap leaked to other users");
        });

        test("only the creator can cancel", () -> {
            SeekManager sm = new SeekManager(null);
            ZugUser creator = newUser(), other = newUser();
            ZugChallenge c = sm.addChallenge(new ZugSeek(creator), null).orElseThrow();
            assertFalse(sm.cancelChallenge(c.getID(), other), "stranger cancelled");
            assertTrue(sm.getChallenge(c.getID()).isPresent(), "vanished after failed cancel");
            assertTrue(sm.cancelChallenge(c.getID(), creator), "creator couldn't cancel");
            assertFalse(sm.getChallenge(c.getID()).isPresent(), "still there after cancel");
        });

        test("single use: concurrent claims yield exactly one winner", () -> {
            for (int round = 0; round < 50; round++) {
                SeekManager sm = new SeekManager(null);
                ZugChallenge c = sm.addChallenge(new ZugSeek(newUser()), null).orElseThrow();
                int racers = 8;
                AtomicInteger winners = new AtomicInteger();
                CountDownLatch go = new CountDownLatch(1), done = new CountDownLatch(racers);
                for (int i = 0; i < racers; i++) new Thread(() -> {
                    try { go.await(); } catch (InterruptedException ignored) { }
                    if (sm.claimChallenge(c)) winners.incrementAndGet();
                    done.countDown();
                }).start();
                go.countDown();
                done.await();
                assertEquals(1, winners.get(), "winners in round " + round);
            }
        });

        test("expiry: hidden, unclaimable and purgeable", () -> {
            SeekManager sm = new SeekManager(null);
            sm.setChallengeTTL(20);
            ZugChallenge c = sm.addChallenge(new ZugSeek(newUser()), null).orElseThrow();
            ZugChallenge d = sm.addChallenge(new ZugSeek(newUser()), null).orElseThrow();
            Thread.sleep(60);
            assertFalse(sm.claimChallenge(c), "claimed an expired challenge");
            assertFalse(sm.getChallenge(c.getID()).isPresent(), "expired challenge visible");
            List<ZugChallenge> purged = sm.purgeExpired();
            assertEquals(1, purged.size(), "purged count (c was already dropped by the lookup)");
            assertEquals(d.getID(), purged.get(0).getID(), "wrong challenge purged");
        });

        test("dropUser removes that user's challenges only", () -> {
            SeekManager sm = new SeekManager(null);
            ZugUser leaver = newUser(), stayer = newUser();
            ZugChallenge gone = sm.addChallenge(new ZugSeek(leaver), null).orElseThrow();
            ZugChallenge kept = sm.addChallenge(new ZugSeek(stayer), null).orElseThrow();
            sm.dropUser(leaver);
            assertFalse(sm.getChallenge(gone.getID()).isPresent(), "leaver's challenge survived");
            assertTrue(sm.getChallenge(kept.getID()).isPresent(), "stayer's challenge removed");
        });

        test("settings are copied and stripped of routing fields", () -> {
            SeekManager sm = new SeekManager(null);
            ObjectNode raw = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    .put("time_control", "5+3")
                    .put(ZugFields.AREA_ID, "hijack")
                    .put(ZugFields.AREA_TITLE, "hijack")
                    .put(ZugFields.AUTO_JOIN, false);
            ZugChallenge c = sm.addChallenge(new ZugSeek(newUser()), raw).orElseThrow();
            ObjectNode settings = c.getSettings();
            assertEquals("5+3", settings.get("time_control").asText(), "setting lost");
            assertFalse(settings.has(ZugFields.AREA_ID), "area_id leaked");
            assertFalse(settings.has(ZugFields.AREA_TITLE), "area_title leaked");
            assertFalse(settings.has(ZugFields.AUTO_JOIN), "auto_join leaked");
            assertTrue(raw.has(ZugFields.AREA_ID), "caller's node was mutated");
        });

        test("JSON shape", () -> {
            SeekManager sm = new SeekManager(null);
            ZugChallenge c = sm.addChallenge(new ZugSeek(newUser()), null).orElseThrow();
            ObjectNode json = c.toJSON();
            assertEquals(c.getID(), json.get("challenge_id").asText(), "challenge_id");
            assertTrue(json.get("expires").asLong() > System.currentTimeMillis(), "expires");
            assertTrue(json.get("creator").has("name"), "creator name");
            assertTrue(json.has("data"), "data");
        });

        System.out.println("\nPassed: " + testsPassed + ", Failed: " + testsFailed);
        if (testsFailed > 0) System.exit(1);
    }

    /** A ZugUser whose Connection swallows everything (challenge logic never needs a real socket). */
    private static ZugUser newUser() {
        Connection conn = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == long.class) return 0L;
                    if (r == int.class) return 0;
                    if (r == double.class) return 0d;
                    return null;
                });
        return new ZugUser(conn, new ZugUser.UniqueName("tester" + (userCount++), ZugAuthSource.none));
    }

    private static void test(String name, TestCase test) {
        try {
            System.out.print("Testing: " + name + " ... ");
            test.run();
            System.out.println("PASS");
            testsPassed++;
        } catch (AssertionError e) {
            System.out.println("FAIL: " + e.getMessage());
            testsFailed++;
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            testsFailed++;
        }
    }

    private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void assertFalse(boolean condition, String message) { if (condition) throw new AssertionError(message); }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + " (expected: " + expected + ", got: " + actual + ")");
    }

    @FunctionalInterface
    private interface TestCase { void run() throws Exception; }
}
