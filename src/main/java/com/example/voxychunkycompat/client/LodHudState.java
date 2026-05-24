package com.example.voxychunkycompat.client;

/**
 * Shared state written by the server thread (singleplayer) or the
 * network thread (dedicated server) and read by the render thread.
 * All fields are volatile for cross-thread visibility.
 */
public final class LodHudState {

    public static volatile int processed = 0;
    public static volatile int total = 0;
    /** Persists across sessions; toggled by /voxyhud. */
    public static volatile boolean hudEnabled = true;
    private static volatile long firstUpdateMs = 0;
    private static volatile long lastUpdateMs = 0;

    /** Called by the progress packet handler when a new delivery batch starts or ticks. */
    public static void update(int newProcessed, int newTotal) {
        long now = System.currentTimeMillis();
        if (newTotal != total || newProcessed < processed) {
            // New batch — reset ETA baseline.
            firstUpdateMs = now;
        }
        processed = newProcessed;
        total = newTotal;
        lastUpdateMs = now;
    }

    /** Hide the HUD immediately (delivery finished or player left). */
    public static void clear() {
        total = 0;
        processed = 0;
        lastUpdateMs = 0;
    }

    /** True while the HUD should be shown. Auto-hides 5 s after last update. */
    public static boolean isActive() {
        if (!hudEnabled) return false;
        if (total <= 0 || processed >= total) return false;
        return lastUpdateMs > 0 && (System.currentTimeMillis() - lastUpdateMs) < 5_000;
    }

    /**
     * Estimated seconds remaining based on the observed processing rate.
     * Returns -1 when there is not yet enough data to estimate.
     */
    public static long etaSeconds() {
        if (processed <= 0 || total <= 0 || firstUpdateMs == 0) return -1;
        long elapsed = System.currentTimeMillis() - firstUpdateMs;
        if (elapsed < 500) return -1; // need at least half a second of data
        double ratePerMs = (double) processed / elapsed;
        int remaining = total - processed;
        if (remaining <= 0) return 0;
        return (long) (remaining / ratePerMs / 1000.0);
    }

    private LodHudState() {}
}
