package io.freemytv;

import java.util.Calendar;
import java.util.TimeZone;

/** Checks Sports on a computer: `tools/test.sh`. */
public final class SportsTest {
    private static int failed, passed;

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
        eq("ESPN's time without seconds", at(31, 10, 30), Sports.time("2026-10-31T17:30Z"));
        eq("with seconds", at(31, 10, 30), Sports.time("2026-10-31T17:30:00Z"));
        eq("unreadable: 0", 0L, Sports.time("soon"));
        eq("in", Sports.IN, Sports.state("in"));
        eq("anything else is to come", Sports.PRE, Sports.state(""));

        long now = at(30, 12, 30);   // Friday
        eq("today", 0, Sports.daysFrom(now, at(30, 23, 59)));
        eq("tomorrow", 1, Sports.daysFrom(now, at(31, 0, 1)));
        eq("yesterday", -1, Sports.daysFrom(now, at(29, 23, 0)));
        eq("over DST's end (Nov 1)", 2, Sports.daysFrom(now, at(32, 12, 0)));

        Sports.Team t = new Sports.Team("TOT", "Premier League");
        t.results.add(game(Sports.POST, at(24, 9, 30), 2, 0));
        t.results.add(game(Sports.POST, at(19, 12, 0), 1, 1));
        t.results.add(game(Sports.POST, at(10, 9, 30), 0, 3));
        t.next = game(Sports.PRE, at(31, 10, 30), -1, -1);
        eq("form, newest first", "WDL", Sports.form(t, 5));
        check("a week-old result gives way to the next game", Sports.shown(t, now) == t.next);
        t.next.state = Sports.IN;
        check("a game on shows first", Sports.shown(t, now) == t.next);
        // It ended at 12:20 pm on Saturday, and the feed still names it as the next game.
        t.next.state = Sports.POST;
        t.next.us = 2;
        t.next.them = 1;
        check("the result stays that evening", Sports.shown(t, at(31, 21, 0)) == t.next);
        check("and until 6 am", Sports.shown(t, at(32, 5, 59)) == t.next);
        check("not after", !Sports.recent(t.next.at, at(32, 6, 0)));

        Sports.Weekend w = new Sports.Weekend();
        Sports.Session fp1 = session("Practice 1", Sports.POST, at(30, 11, 30)), fp2 = session("Practice 2", Sports.PRE, at(30, 15, 0));
        Sports.Session race = session("Race", Sports.PRE, at(32, 12, 0));
        w.sessions.add(fp1);
        w.sessions.add(fp2);
        w.sessions.add(race);
        check("the next session", Sports.shown(w, now) == fp2);
        check("then the race", Sports.after(w, fp2) == race);
        fp2.state = Sports.IN;
        check("the session on now", Sports.shown(w, now) == fp2);
        fp2.state = race.state = Sports.POST;
        check("the race's result until the next morning", Sports.shown(w, at(32, 20, 0)) == race);

        eq("a minute while live", Sports.LIVE_EVERY, Sports.every(true, 0, now));
        eq("a minute 10 minutes before a start", Sports.LIVE_EVERY, Sports.every(false, now + 10 * 60_000L, now));
        eq("half an hour otherwise", Sports.CALM_EVERY, Sports.every(false, now + 3600_000L, now));

        Sports.Game live = game(Sports.IN, at(31, 10, 30), -1, -1);
        check("a live game with no score counts as 0–0", Sports.usable(live) == live && live.us == 0 && live.them == 0);
        check("a finished game with no score (postponed) is no result", Sports.usable(game(Sports.POST, at(31, 10, 30), -1, -1)) == null);
        check("a game to come is kept", Sports.usable(game(Sports.PRE, at(31, 10, 30), -1, -1)) != null);
        Sports.Game kick = game(Sports.PRE, at(31, 10, 30), -1, -1);
        check("watched from 15 minutes before", Sports.watching(kick, at(31, 10, 16)));
        check("not 20 minutes before", !Sports.watching(kick, at(31, 10, 10)));
        check("until 4 hours after", Sports.watching(kick, at(31, 14, 29)));
        check("not after", !Sports.watching(kick, at(31, 14, 31)));
        check("with no game known: look", Sports.watching(null, now));
        eq("the scoreboard day is Eastern time", "20261028", Sports.etDay(Sports.time("2026-10-29T02:30Z")));
        java.util.List<Sports.Game> list = new java.util.ArrayList<>(t.results);
        Sports.addResult(list, game(Sports.POST, at(31, 12, 0), 2, 1));
        eq("a game just finished joins the results first", 2, list.get(0).us);
        Sports.addResult(list, game(Sports.POST, at(31, 12, 0), 2, 1));
        eq("but not twice", 4, list.size());
        Sports.addResult(list, game(Sports.POST, at(31, 13, 0), -1, -1));
        eq("nor without a score", 4, list.size());
        eq("a minute 1 hour after a start that's still 'not started'", Sports.LIVE_EVERY, Sports.every(false, now - 3600_000L, now));
        eq("half an hour when it's 2 days old", Sports.CALM_EVERY, Sports.every(false, now - 48 * 3600_000L, now));

        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        if (failed > 0) System.exit(1);
    }

    private static Sports.Game game(int state, long at, int us, int them) {
        Sports.Game g = new Sports.Game();
        g.state = state;
        g.at = at;
        g.us = us;
        g.them = them;
        return g;
    }

    private static Sports.Session session(String name, int state, long at) {
        Sports.Session s = new Sports.Session();
        s.name = name;
        s.state = state;
        s.at = at;
        return s;
    }

    /** October `day`, 2026 (32 is November 1), at `hour`:`minute` on the TV's clock. */
    private static long at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(2026, Calendar.OCTOBER, day, hour, minute, 0);
        return c.getTimeInMillis();
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
