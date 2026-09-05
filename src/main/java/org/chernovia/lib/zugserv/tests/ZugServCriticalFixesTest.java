// ============================================================================
// ZugServ Critical Fixes - Standalone Tests (No Dependencies)
// ============================================================================
// Run with: java -cp target/classes:target/test-classes ZugServCriticalFixesTest
// Or just run as a normal Java application

package org.chernovia.lib.zugserv.tests;

import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ZugServCriticalFixesTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("ZugServ Critical Fixes Test Suite");
        System.out.println("========================================\n");

        // Issue #1 Tests
        testPhaseManagerShutdown();
        testPhaseManagerInterruptedShutdown();

        // Issue #2 Tests
        testResponseManagerCleanupOnTimeout();
        testResponseManagerConcurrentRequests();

        // Issue #3 Tests
        testTimeoutableConcurrentActions();
        testTimeoutableTimeout();
        testTimeoutableMillisecondPrecision();
        testTimeoutableNegativeTimeout();

        // Issue #4 Tests
        testBanAddressMatchBounds();

        // Issue #5 Tests
        testMessageManagerBoundedSize();
        testMessageManagerNullHandling();
        testMessageManagerConcurrentAdds();

        // Print summary
        System.out.println("\n========================================");
        System.out.println("Test Results");
        System.out.println("========================================");
        System.out.println("Passed: " + testsPassed);
        System.out.println("Failed: " + testsFailed);
        System.out.println("Total:  " + (testsPassed + testsFailed));
        System.out.println("========================================\n");

        if (testsFailed == 0) {
            System.out.println("✓ All tests passed!");
        } else {
            System.out.println("✗ Some tests failed!");
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    private static void test(String name, TestCase test) {
        try {
            System.out.print("Testing: " + name + " ... ");
            test.run();
            System.out.println("✓ PASS");
            testsPassed++;
        } catch (AssertionError e) {
            System.out.println("✗ FAIL");
            System.out.println("  Error: " + e.getMessage());
            testsFailed++;
        } catch (Exception e) {
            System.out.println("✗ ERROR");
            System.out.println("  Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            testsFailed++;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", got: " + actual + ")");
        }
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }

    // ========================================================================
    // ISSUE #1: PhaseManager Thread Leak Tests
    // ========================================================================

    private static void testPhaseManagerShutdown() {
        test("PhaseManager should properly shutdown scheduler", () -> {
            MockZugArea area = new MockZugArea("TestArea");
            PhaseManager pm = new PhaseManager(area);

            pm.newPhase(TestPhase.PHASE1, 1000);
            pm.close();

            assertTrue(pm.getPhase().equals(TestPhase.PHASE1), "Phase should be recorded");
        });
    }

    private static void testPhaseManagerInterruptedShutdown() {
        test("PhaseManager should handle interrupted shutdown gracefully", () -> {
            MockZugArea area = new MockZugArea("TestArea");
            PhaseManager pm = new PhaseManager(area);

            pm.newPhase(TestPhase.PHASE1, 5000);
            pm.shutdownPhases(); // Should not throw
        });
    }

    // ========================================================================
    // ISSUE #2: ResponseManager Memory Leak Tests
    // ========================================================================

    private static void testResponseManagerCleanupOnTimeout() {
        test("ResponseManager should cleanup on timeout", () -> {
            MockZugArea area = new MockZugArea("TestArea");
            ResponseManager rm = new ResponseManager(area);

            String responseType = "timeout_response";
            CompletableFuture<java.util.List<ResponseManager.OccupantResponse>> future =
                    rm.requestResponse(responseType, 1);

            java.util.List<ResponseManager.OccupantResponse> result =
                    future.get(3, TimeUnit.SECONDS);

            assertNotNull(result, "Should complete with results after timeout");
        });
    }

    private static void testResponseManagerConcurrentRequests() {
        test("ResponseManager should handle multiple concurrent requests", () -> {
            MockZugArea area = new MockZugArea("TestArea");
            ResponseManager rm = new ResponseManager(area);

            CompletableFuture<java.util.List<ResponseManager.OccupantResponse>> future1 =
                    rm.requestResponse("response_1", 5);
            CompletableFuture<java.util.List<ResponseManager.OccupantResponse>> future2 =
                    rm.requestResponse("response_2", 5);

            assertNotNull(future1, "First response request should be created");
            assertNotNull(future2, "Second response request should be created");
        });
    }

    // ========================================================================
    // ISSUE #3: Timeoutable Thread Safety Tests
    // ========================================================================

    private static void testTimeoutableConcurrentActions() {
        test("Timeoutable should be thread-safe for concurrent action() calls", () -> {
            MockTimeoutable obj = new MockTimeoutable();
            obj.setIdleTimeout(10);

            AtomicInteger errorCount = new AtomicInteger(0);
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount * 100);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        try {
                            obj.action(Timeoutable.ActionType.user);
                            latch.countDown();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            latch.countDown();
                        }
                    }
                });
            }

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertTrue(completed, "All threads should complete");
            assertEquals(0, errorCount.get(), "No exceptions should occur");

            executor.shutdown();
        });
    }

    private static void testTimeoutableTimeout() {
        test("Timeoutable should timeout correctly", () -> {
            MockTimeoutable obj = new MockTimeoutable();
            obj.setIdleTimeout(1);

            assertFalse(obj.timedOut(), "Should not timeout immediately");

            Thread.sleep(1500);
            assertTrue(obj.timedOut(), "Should timeout after 1 second");

            obj.action(Timeoutable.ActionType.user);
            assertFalse(obj.timedOut(), "Should not timeout after action");
        });
    }

    private static void testTimeoutableMillisecondPrecision() {
        test("Timeoutable should have millisecond precision", () -> {
            MockTimeoutable obj = new MockTimeoutable();
            obj.setIdleTimeout(1);

            long elapsedMillis = obj.getMillisSinceLastAction();
            assertTrue(elapsedMillis < 100, "Should have millisecond precision (was: " + elapsedMillis + "ms)");
        });
    }

    private static void testTimeoutableNegativeTimeout() {
        test("Timeoutable should reject negative timeouts", () -> {
            MockTimeoutable obj = new MockTimeoutable();
            obj.setIdleTimeout(-5);

            assertTrue(obj.getIdleTimeout() > 0, "Negative timeout should be rejected");
        });
    }

    // ========================================================================
    // ISSUE #4: Ban.addressMatch() Bounds Tests
    // ========================================================================

    private static void testBanAddressMatchBounds() {
        test("Ban should handle address matching without bounds errors", () -> {
            MockZugUser bannedUser = new MockZugUser("user1", "192.168.1.1");
            MockZugUser bannor = new MockZugUser("admin", "10.0.0.1");

            Ban ban = new Ban(bannedUser, 60000, bannor);
            assertTrue(ban.inEffect(bannedUser), "Ban should be in effect for banned user");
        });
    }

    // ========================================================================
    // ISSUE #5: MessageManager Performance Tests
    // ========================================================================

    private static void testMessageManagerBoundedSize() {
        test("MessageManager should maintain bounded size", () -> {
            MessageManager mm = new MessageManager();

            for (int i = 0; i < 150; i++) {
                ObjectNode msg = ZugUtils.newJSON().put("index", i);
                mm.addMessage(msg);
            }

            assertTrue(mm.size() <= 99, "Message manager should stay at or below 99 messages (was: " + mm.size() + ")");
        });
    }

    private static void testMessageManagerNullHandling() {
        test("MessageManager should handle null messages gracefully", () -> {
            MessageManager mm = new MessageManager();
            mm.addMessage(null);

            assertEquals(0, mm.size(), "Null message should not be added");
        });
    }

    private static void testMessageManagerConcurrentAdds() {
        test("MessageManager should be thread-safe for concurrent adds", () -> {
            MessageManager mm = new MessageManager();

            int threadCount = 10;
            int messagesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < messagesPerThread; i++) {
                        ObjectNode msg = ZugUtils.newJSON()
                                .put("thread", threadId)
                                .put("index", i);
                        mm.addMessage(msg);
                    }
                    latch.countDown();
                });
            }

            boolean completed = latch.await(5, TimeUnit.SECONDS);
            assertTrue(completed, "All threads should complete");
            assertTrue(mm.size() <= 99, "Should maintain bounded size under concurrent load (was: " + mm.size() + ")");

            executor.shutdown();
        });
    }

    // ========================================================================
    // Test Fixtures / Mocks
    // ========================================================================

    enum TestPhase {
        PHASE1, PHASE2, PHASE3
    }

    static class MockZugArea extends ZugArea<ZugServCriticalFixesTest.MockOccupant> {
        public MockOccupant mockOccupant1;
        public MockOccupant mockOccupant2;

        public MockZugArea(String title) {
            super(title, new MockZugUser("creator", "10.0.0.1"), new MockAreaListener());

            mockOccupant1 = new MockOccupant(new MockZugUser("user1", "192.168.1.1"), this);
            mockOccupant2 = new MockOccupant(new MockZugUser("user2", "192.168.1.2"), this);
        }

        @Override
        public String getName() { return getID(); }
    }

    static class MockOccupant extends Occupant<MockOccupant> {
        public MockOccupant(ZugUser user, ZugArea<MockOccupant> area) {
            super(user, area);
        }
    }

    static class MockZugUser extends ZugUser {
        public MockZugUser(String name, String address) {
            super(new MockConnection(address), new UniqueName(name, ZugAuthSource.none));
        }
    }

    static class MockConnection extends ConnAdapter {
        private String address;

        public MockConnection(String address) {
            this.address = address;
            setID(System.nanoTime());
            setRemoteAddress(address);
        }

        @Override
        public void close(String reason) { }

        @Override
        public void tell(Enum<?> type, String msg) { }

        @Override
        public void tell(Enum<?> type, com.fasterxml.jackson.databind.JsonNode msg) { }
    }

    static class MockAreaListener implements AreaListener {
        @Override public void areaClosed(ZugArea area) { }
        @Override public void areaStarted(ZugArea area) { }
        @Override public void areaFinished(ZugArea area) { }
        @Override public void areaCreated(ZugArea area) { }
        @Override public void areaUpdated(ZugArea area, String updateType) { }
        @Override public void areaParted(ZugArea area, ZugUser user) { }
        @Override public void areaJoined(ZugArea area, Occupant occupant) { }
    }

    static class MockTimeoutable extends Timeoutable {
        // Concrete implementation for testing
    }
}
