package io.freemytv;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The owner's teams and the F1 weekend from ESPN's public feed, as plain data, and what the board shows of them: the
 * live game, a result until the next morning, or what's coming up. Only java.* here (tools/test.sh: SportsTest);
 * HomeData reads the JSON into these.
 */
final class Sports {
    private Sports() {}

    static final int PRE = 0, IN = 1, POST = 2;
    /** While something is on or about to start, every minute; otherwise every half hour. */
    static final long LIVE_EVERY = 60_000L, CALM_EVERY = 30 * 60_000L, SOON = 15 * 60_000L;

    /** One game, from our side: `us` and `them` are -1 before it starts. */
    static final class Game {
        String opp = "", oppAbbr = "", clock = "";
        long at;
        int state, us = -1, them = -1;
        boolean home;
    }

    static final class Team {
        final String abbr, league;
        String name = "", standing = "";
        Game next;
        /** Finished games, newest first. */
        final List<Game> results = new ArrayList<>();

        Team(String abbr, String league) {
            this.abbr = abbr;
            this.league = league;
        }
    }

    /** An F1 session: `lap` and `leader` while it runs, the winner after. */
    static final class Session {
        String name = "", leader = "";
        long at;
        int state, lap;
    }

    static final class Weekend {
        String name = "";
        final List<Session> sessions = new ArrayList<>();
    }

    /** ESPN's times ("2026-10-30T18:30Z", seconds optional); 0 when it can't be read. */
    static long time(String iso) {
        try {
            // Android's Instant.parse wants the seconds, which ESPN leaves out.
            return Instant.parse(iso.length() == 17 ? iso.replace("Z", ":00Z") : iso).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static int state(String espn) {
        return "in".equals(espn) ? IN : "post".equals(espn) ? POST : PRE;
    }

    /** 'W', 'D' or 'L' for a finished game. */
    static char result(Game g) {
        return g.us > g.them ? 'W' : g.us == g.them ? 'D' : 'L';
    }

    /** The last `n` results, newest first: "WWDLW". */
    static String form(Team t, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(n, t.results.size()); i++) s.append(result(t.results.get(i)));
        return s.toString();
    }


    /**
     * A game the board can show: a live score is never below 0 (ESPN may leave it out), and a finished game with no
     * score (postponed, cancelled) isn't a result, so it's null.
     */
    static Game usable(Game g) {
        if (g == null) return null;
        if (g.state == IN) {
            g.us = Math.max(0, g.us);
            g.them = Math.max(0, g.them);
        }
        return g.state == POST && (g.us < 0 || g.them < 0) ? null : g;
    }

    /**
     * Whether to look at the league's scoreboard for the game we were waiting for (`g`, or none): from 15 minutes
     * before it starts until 4 hours after. ESPN's team page moves on to the next fixture as soon as one starts, so
     * only the scoreboard has the game in play.
     */
    static boolean watching(Game g, long now) {
        return g == null || now >= g.at - SOON && now < g.at + 4 * 3600_000L;
    }

    /** ESPN's scoreboard day for a game: its date in US Eastern time ("20261028" for 2026-10-29 02:30 UTC). */
    static String etDay(long at) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("America/New_York"));
        return f.format(at);
    }

    /** A game just seen finished on the scoreboard joins the results (newest first, five kept) unless they have it. */
    static void addResult(List<Game> results, Game fin) {
        if (fin == null || fin.us < 0 || fin.them < 0 || !results.isEmpty() && results.get(0).at >= fin.at) return;
        results.add(0, fin);
        while (results.size() > 5) results.remove(5);
    }

    /** A result stays on the board until 6 am the morning after the game. */
    static boolean recent(long at, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 6);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return now < c.getTimeInMillis();
    }

    /**
     * The team's game to show: the one being played, else a result from last night or today, else the next one.
     * The feed's next game can still be the one that just ended, so a finished `next` counts as a result.
     */
    static Game shown(Team t, long now) {
        Game last = t.results.isEmpty() ? null : t.results.get(0);
        Game next = t.next;
        if (next != null && next.state == IN) return next;
        if (next != null && next.state == POST && (last == null || next.at > last.at)) last = next;
        if (last != null && recent(last.at, now)) return last;
        return next != null && next.state == PRE ? next : last;
    }

    /** The session to show: the one running, else a race result until the next morning, else the next session. */
    static Session shown(Weekend w, long now) {
        Session next = null, lastRace = null;
        for (Session s : w.sessions) {
            if (s.state == IN) return s;
            if (s.state == PRE && (next == null || s.at < next.at)) next = s;
            if (s.state == POST && "Race".equals(s.name)) lastRace = s;
        }
        if (lastRace != null && recent(lastRace.at, now)) return lastRace;
        return next != null ? next : lastRace;
    }

    /** The first session after `s` that hasn't run, or null. */
    static Session after(Weekend w, Session s) {
        Session best = null;
        for (Session x : w.sessions) if (x.state == PRE && x.at > s.at && (best == null || x.at < best.at)) best = x;
        return best;
    }

    /** Whole days from today to `at` on the TV's clock: 0 today, 1 tomorrow, -1 yesterday. */
    static int daysFrom(long now, long at) {
        return (int) (dayNumber(at) - dayNumber(now));
    }

    private static long dayNumber(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        return Math.floorDiv(t + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET), 86_400_000L);
    }

    /** How long until the next fetch: a minute while anything is on or starts within 15 minutes (or began in the last 3 hours). */
    static long every(boolean live, long soonest, long now) {
        return live || soonest > now - 3 * 3600_000L && soonest - now < SOON ? LIVE_EVERY : CALM_EVERY;
    }
}
