package io.freemytv;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * One feed's own thread, one run at a time. A run that is still going after a minute is given up on: a new thread takes
 * over, and the old one's `current(gen)` turns false, so it can't write its late answer over a newer one.
 */
final class Lane {
    static final long STUCK_MS = 60_000L;
    private final long stuckMs;
    private final Consumer<String> log;
    private ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean busy;
    private long since;
    private int gen;

    Lane(Consumer<String> log) {
        this(STUCK_MS, log);
    }

    Lane(long stuckMs, Consumer<String> log) {
        this.stuckMs = stuckMs;
        this.log = log;
    }

    /** A run is under way (and not stuck). */
    synchronized boolean busy(long now) {
        if (busy && now - since > stuckMs) {
            log.accept("stuck for " + (now - since) / 1000 + " s, starting over");
            io.shutdownNow();
            io = Executors.newSingleThreadExecutor();
            gen++;
            busy = false;
        }
        return busy;
    }

    /** Runs `job` on the lane's thread; it is given its generation. */
    synchronized void run(long now, IntConsumer job) {
        busy = true;
        since = now;
        final int mine = gen;
        io.execute(() -> {
            try {
                job.accept(mine);
            } finally {
                synchronized (this) {
                    if (mine == gen) busy = false;
                }
            }
        });
    }

    /** False once the run with this generation was given up on. */
    synchronized boolean current(int mine) {
        return mine == gen;
    }
}
