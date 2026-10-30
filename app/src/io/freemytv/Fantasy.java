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

    /** How often to ask Sleeper and FPL again: every 5 minutes. */
    static final long EVERY = 5 * 60_000L;
}
