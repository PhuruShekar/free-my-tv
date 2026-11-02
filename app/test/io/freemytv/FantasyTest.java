package io.freemytv;

import java.util.List;

/** Checks Fantasy on a computer: `tools/test.sh`. */
public final class FantasyTest {
    private static int failed, passed;

    public static void main(String[] args) {
        eq("the rank fell: up", Fantasy.UP, Fantasy.arrow(3, 4));
        eq("the rank grew: down", Fantasy.DOWN, Fantasy.arrow(5, 4));
        eq("the same", Fantasy.SAME, Fantasy.arrow(3, 3));
        eq("no last rank: none", Fantasy.NONE, Fantasy.arrow(3, 0));

        long hr = 3600_000L, t0 = 1_000_000_000_000L;
        Fantasy.Games g = new Fantasy.Games();
        eq("never asked: now", 0L, g.due(t0));
        g.update(false, new long[] {t0 + 3 * 24 * hr, t0 + 4 * 24 * hr}, false, t0);
        check("days before the first kickoff: not a game week", !g.active(t0));
        eq("so only a look at the feed, in 6 hours", t0 + 6 * hr, g.due(t0));
        g.update(false, new long[] {t0 + 3 * hr, t0 + 28 * hr}, false, t0);
        eq("a week that opens in 1 hour: looked at when it opens", t0 + hr, g.due(t0));
        check("2 hours before the first kickoff it's active", g.active(t0 + hr));
        g.update(false, new long[] {t0 + 3 * hr, t0 + 28 * hr}, false, t0 + hr);
        eq("in the week, hourly", t0 + 2 * hr, g.due(t0 + hr));
        g.update(false, new long[] {t0 + 3 * hr, t0 + 28 * hr}, false, t0 + 2 * hr + hr / 2);
        eq("but at the next kickoff if that's sooner", t0 + 3 * hr + Fantasy.KICKOFF_LAG, g.due(t0 + 2 * hr + hr / 2));
        g.update(true, new long[] {t0 + 3 * hr, t0 + 28 * hr}, false, t0 + 3 * hr);
        eq("a game on: every 3 minutes", t0 + 3 * hr + 3 * 60_000L, g.due(t0 + 3 * hr));
        g.update(false, new long[] {t0 - 30 * hr, t0 - 2 * hr}, false, t0);
        check("5 hours after the last kickoff the week is over", !g.active(t0 + 4 * hr));
        eq("then only looks, every 6 hours", t0 + 6 * hr, g.due(t0 + 4 * hr));
        g.update(false, new long[0], false, t0);
        check("an empty feed (off season): not active", !g.active(t0));
        g.update(false, new long[] {t0 - 30 * hr}, true, t0);
        check("held open (a gameweek not finished): active", g.active(t0 + 20 * hr));
        g.update(false, new long[] {t0 - hr, t0 + 20 * hr}, false, t0);
        check("in a game week, not due for an hour", g.due(t0) > t0 + 30 * 60_000L);
        g.retry();
        eq("but after the scores failed: at once", 0L, g.due(t0 + 1000));
        eq("a clock set back asks at once", 0L, g.due(t0 - hr));

        eq("1st", "1st", Fantasy.ordinal(1));
        eq("2nd", "2nd", Fantasy.ordinal(2));
        eq("3rd", "3rd", Fantasy.ordinal(3));
        eq("4th", "4th", Fantasy.ordinal(4));
        eq("11th, not 11st", "11th", Fantasy.ordinal(11));
        eq("12th", "12th", Fantasy.ordinal(12));
        eq("13th", "13th", Fantasy.ordinal(13));
        eq("21st", "21st", Fantasy.ordinal(21));
        eq("112th", "112th", Fantasy.ordinal(112));

        // Wins 6, 5, 5, 3: the two on 5 go by points.
        int[] wins = {6, 5, 5, 3}, ties = {0, 0, 0, 0};
        double[] points = {900, 800, 850, 700};
        eq("most wins is first", 1, Fantasy.place(0, wins, ties, points));
        eq("level on wins: more points first", 2, Fantasy.place(2, wins, ties, points));
        eq("and the one below", 3, Fantasy.place(1, wins, ties, points));
        eq("last", 4, Fantasy.place(3, wins, ties, points));
        eq("a tie counts as half a win", 2, Fantasy.place(1, new int[] {5, 5}, new int[] {1, 0}, new double[] {1, 1}));

        Fantasy.Team t = new Fantasy.Team();
        check("0 and 0: not started", !Fantasy.started(t));
        t.theirs = 12.5;
        check("any points: started", Fantasy.started(t));
        t.lastMine = 98.1;
        t.lastTheirs = 112.4;
        eq("last week lost", 'L', Fantasy.lastResult(t));
        t.lastMine = 120;
        eq("last week won", 'W', Fantasy.lastResult(t));

        long now = 1_000_000_000L, h = 3600_000L;
        check("a deadline in 47 hours is soon", Fantasy.deadlineSoon(now + 47 * h, now));
        check("in 49 hours isn't", !Fantasy.deadlineSoon(now + 49 * h, now));
        check("one that has passed isn't", !Fantasy.deadlineSoon(now - h, now));

        Fantasy.Fpl f = new Fantasy.Fpl();
        f.start = now + 20 * h;
        f.end = now + 80 * h;
        f.deadline = now + 18 * h;
        List<long[]> between = Fantasy.times(f, now);
        eq("between gameweeks: the start and the deadline", 2, between.size());
        eq("the start first", 0L, between.get(0)[1]);
        eq("then the deadline", 2L, between.get(1)[1]);
        f.live = true;
        List<long[]> live = Fantasy.times(f, now);
        eq("live: only when it ends", 1, live.size());
        eq("which is its end", f.end, live.get(0)[0]);
        f.live = false;
        f.deadline = now - h;
        eq("a deadline that passed isn't listed", 1, Fantasy.times(f, now).size());

        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        if (failed > 0) System.exit(1);
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
