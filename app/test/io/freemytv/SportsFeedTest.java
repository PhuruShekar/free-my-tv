package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks SportsFeed against feeds saved from ESPN (app/test/feeds): `tools/test.sh`, which needs tools/lib/json-*.jar. */
public final class SportsFeedTest {
    private static int failed, passed;

    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));

        // The team page: Spurs (id 367) at home to Crystal Palace, not started, no scores yet.
        JSONObject team = json("team.json").getJSONObject("team");
        Sports.Game next = SportsFeed.game(team.getJSONArray("nextEvent").getJSONObject(0), "367");
        eq("a game to come", Sports.PRE, next.state);
        eq("at home", true, next.home);
        eq("against", "Crystal Palace", next.opp);
        eq("abbreviated", "CRY", next.oppAbbr);
        eq("its time (seconds are missing in ESPN's)", Sports.time("2026-10-31T17:30Z"), next.at);
        eq("no scores yet", -1, next.us);
        eq("the standing line", "14th in English Premier League", team.getString("standingSummary"));

        // The schedule: results with scores, from our side.
        JSONArray events = json("schedule.json").getJSONArray("events");
        Sports.Game won = SportsFeed.game(events.getJSONObject(0), "367");
        eq("a finished game", Sports.POST, won.state);
        eq("our goals", 2, won.us);
        eq("theirs", 0, won.them);
        eq("a win", 'W', Sports.result(won));
        eq("away at Chelsea", false, won.home);

        // A game in play, from a league scoreboard (Espanyol–Villarreal, taken while it was on).
        JSONObject e = json("live.json").getJSONArray("events").getJSONObject(0);
        Sports.Game live = SportsFeed.game(e, "88");
        eq("in play", Sports.IN, live.state);
        check("with a score each way", live.us >= 0 && live.them >= 0);
        check("and a clock", !live.clock.isEmpty());

        // The F1 weekend: five sessions, one of them on when it was saved.
        Sports.Weekend w = SportsFeed.weekend(json("f1.json"));
        eq("the race's name", "Mexico City GP", w.name);
        eq("five sessions", 5, w.sessions.size());
        eq("the first", "Practice 1", w.sessions.get(0).name);
        eq("qualifying", "Qualifying", w.sessions.get(3).name);
        eq("the race", "Race", w.sessions.get(4).name);
        eq("practice 1 was on", Sports.IN, w.sessions.get(0).state);
        eq("with a leader", "C. Leclerc", w.sessions.get(0).leader);
        eq("and a session to come", Sports.PRE, w.sessions.get(1).state);

        eq("a score as a number", 3, SportsFeed.score(3.0));
        eq("as text", 2, SportsFeed.score("2"));
        eq("missing", -1, SportsFeed.score(null));
        eq("as an object", 4, SportsFeed.score(new JSONObject("{\"displayValue\":\"4\"}")));
        eq("unreadable", -1, SportsFeed.score("x"));

        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        if (failed > 0) System.exit(1);
    }

    private static JSONObject json(String name) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Paths.get("app/test/feeds/" + name)), StandardCharsets.UTF_8));
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
