package io.freemytv;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks Lane on a computer: `tools/test.sh`. */
public final class LaneTest {
    private static int failed, passed;

    public static void main(String[] args) throws Exception {
        AtomicInteger logged = new AtomicInteger();
        Lane lane = new Lane(1000, m -> logged.incrementAndGet());
        long t = 1_000_000L;
        check("idle at first", !lane.busy(t));

        CountDownLatch release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger firstGen = new AtomicInteger(-1);
        lane.run(t, gen -> {
            firstGen.set(gen);
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        });
        check("the job started", started.await(5, TimeUnit.SECONDS));
        check("busy while it runs", lane.busy(t + 500));
        check("its generation is current", lane.current(firstGen.get()));
        check("a run that outlasts the limit is given up on", !lane.busy(t + 1500));
        eq("and said so", 1, logged.get());
        check("so its generation is no longer current", !lane.current(firstGen.get()));

        CountDownLatch second = new CountDownLatch(1);
        lane.run(t + 1600, gen -> second.countDown());
        check("a new thread takes over while the old one hangs", second.await(5, TimeUnit.SECONDS));
        release.countDown();
        Thread.sleep(200);
        check("free again after the new run, and the old one ending doesn't undo that", !lane.busy(t + 1700));

        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        System.exit(failed > 0 ? 1 : 0);   // the lane's threads are not daemons
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAILED: " + what);
        }
    }

    private static void eq(String what, Object want, Object got) {
        check(what + " (wanted " + want + ", got " + got + ")", want.equals(got));
    }
}
