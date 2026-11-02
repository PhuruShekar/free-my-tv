package io.freemytv;

import java.util.ArrayList;
import java.util.List;

/**
 * The owner's fantasy teams as plain data (Sleeper's NFL leagues and the Fantasy Premier League strip), and the small
 * decisions the Fantasy row needs. Only java.* here (tools/test.sh: FantasyTest); HomeData reads the JSON into these.
 */
final class Fantasy {
    private Fantasy() {}

    /** The deadline turns amber this long before it. */
    static final long DEADLINE_SOON = 48 * 3600_000L;
    static final int UP = 1, SAME = 0, DOWN = -1, NONE = 2;

    /** One Sleeper team in one league, and its game this week. Points are 0 until the games start. */
    static final class Team {
        String league = "", name = "", opp = "";
        int wins, losses, ties, place, size, week;
        double mine, theirs, lastMine, lastTheirs;
        boolean hasOpp, hasLast;
    }

    /** The FPL strip: this gameweek's points and the mini-league rank, and when the next gameweek starts. */
    static final class Fpl {
        String name = "", league = "";
        int gw, points, rank, lastRank, nextGw;
        boolean live;
        /** The shown gameweek's first and last kickoff (the next one's between gameweeks), and its deadline. */
        long start, end, deadline;
    }

    /** Both teams have 0 points: the week's games haven't started. */
    static boolean started(Team t) {
        return t.mine > 0 || t.theirs > 0;
    }

    /** "W", "D" or "L" for last week's game. */
    static char lastResult(Team t) {
        return t.lastMine > t.lastTheirs ? 'W' : t.lastMine == t.lastTheirs ? 'D' : 'L';
    }

    /** The rank's move since last week: UP when the rank number fell; NONE without last week's. */
    static int arrow(int rank, int last) {
        return rank <= 0 || last <= 0 ? NONE : rank < last ? UP : rank > last ? DOWN : SAME;
    }

    static boolean deadlineSoon(long deadline, long now) {
        return deadline > now && deadline - now <= DEADLINE_SOON;
    }

    /** "1st", "2nd", "3rd", "4th"…, "11th" to "13th". */
    static String ordinal(int n) {
        int tens = n % 100;
        if (tens >= 11 && tens <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    /**
     * The place of team `me` among the league's: most wins (a tie is half), then most points. Arrays go by roster, in
     * any order; the result is 1 for the best.
     */
    static int place(int me, int[] wins, int[] ties, double[] points) {
        int place = 1;
        for (int i = 0; i < wins.length; i++) {
            if (i == me) continue;
            double a = wins[i] + ties[i] / 2.0, b = wins[me] + ties[me] / 2.0;
            if (a > b || a == b && points[i] > points[me]) place++;
        }
        return place;
    }

    /** The FPL strip's two lines under the numbers, as times: what starts (or ends), and the deadline when it's ahead. */
    static List<long[]> times(Fpl f, long now) {
        List<long[]> out = new ArrayList<>();
        if (f.live) {
            if (f.end > 0) out.add(new long[] {f.end, 1});
            return out;
        }
        if (f.start > 0) out.add(new long[] {f.start, 0});
        if (f.deadline > now) out.add(new long[] {f.deadline, 2});
        return out;
    }

    /** While a game is on, every 3 minutes; through the rest of a game week, hourly; between game weeks, no scores. */
    static final long LIVE_EVERY = 3 * 60_000L, WEEK_EVERY = 3600_000L, QUIET_EVERY = 6 * 3600_000L;
    /** A game week opens this long before its first kickoff and closes this long after its last; a kickoff is looked at this long after it. */
    static final long LEAD = 2 * 3600_000L, TAIL = 5 * 3600_000L, KICKOFF_LAG = 60_000L;

    /**
     * What a games feed (ESPN's NFL scoreboard, FPL's fixtures) last said about this game week: when its games start
     * and whether one is on. It decides when to ask again, for the scores (Sleeper, FPL's entry) too.
     */
    static final class Games {
        // Read by the UI thread, written by the fantasy thread: every method is synchronized.
        private long[] starts = new long[0];
        private boolean live, held;
        /** When it was last asked (0: never). */
        private long at;

        /** `held`: the week stays open past its last kickoff (FPL's gameweek isn't finished yet). */
        synchronized void update(boolean live, long[] starts, boolean held, long now) {
            this.live = live;
            this.starts = starts;
            this.held = held;
            this.at = now;
        }

        /** The scores failed to come: ask again as soon as the retry pause is over, not at the next hourly time. */
        synchronized void retry() {
            at = 0;
        }

        /** In a game week (or a game is on): the scores are worth asking for. */
        synchronized boolean active(long now) {
            if (live || held) return true;
            if (starts.length == 0) return false;
            long first = starts[0], last = starts[0];
            for (long s : starts) {
                first = Math.min(first, s);
                last = Math.max(last, s);
            }
            return now >= first - LEAD && now <= last + TAIL;
        }

        /** When to ask again: 0 is now. Outside a game week only the feed is looked at, now and then, to see one coming. */
        synchronized long due(long now) {
            if (at == 0 || now < at) return 0;
            if (live) return at + LIVE_EVERY;
            long t;
            if (active(now)) {
                t = at + WEEK_EVERY;
                for (long s : starts) if (s > at) t = Math.min(t, s + KICKOFF_LAG);
            } else {
                t = at + QUIET_EVERY;
                for (long s : starts) if (s - LEAD > at) t = Math.min(t, s - LEAD);
            }
            return t;
        }
    }
}
