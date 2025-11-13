// ============================================================================
// Auth Service Timeouts
// ============================================================================
// Prevents server hanging when external auth services (Lichess, Firebase, Google) are down

package org.chernovia.lib.zugserv;


import java.util.concurrent.*;

/**
 * Configuration for auth service timeouts.
 *
 * <p>When external auth services don't respond, the server should fail fast
 * rather than hanging connections indefinitely.
 */
public class AuthTimeoutConfig {

    // Timeout for external auth service calls (in seconds)
    public static final int LICHESS_TIMEOUT_SECONDS = 30;
    public static final int FIREBASE_TIMEOUT_SECONDS = 30;
    public static final int GOOGLE_TIMEOUT_SECONDS = 30;

    // Thread pool for async auth calls (prevents blocking main handlers)
    private static final ExecutorService AUTH_EXECUTOR =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "ZugAuth-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            });

    static {
        // Ensure proper shutdown on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!AUTH_EXECUTOR.isShutdown()) {
                AUTH_EXECUTOR.shutdown();
                try {
                    if (!AUTH_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                        AUTH_EXECUTOR.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    AUTH_EXECUTOR.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }));
    }

    public static ExecutorService getAuthExecutor() {
        return AUTH_EXECUTOR;
    }
}

