package io.freemytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** ESPN's JSON as Sports' data. Only org.json here (tools/test.sh: SportsFeedTest, with saved feeds in app/test/feeds). */
final class SportsFeed {
    private SportsFeed() {}

    /** One of ESPN's events, from the side of the team with `id`. */
    static Sports.Game game(JSONObject e, String id) throws JSONException {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        Sports.Game g = new Sports.Game();
        g.at = Sports.time(e.optString("date"));
        JSONObject status = c.optJSONObject("status"), type = status == null ? null : status.optJSONObject("type");
        g.state = Sports.state(type == null ? "" : type.optString("state"));
        g.clock = type == null ? "" : type.optString("shortDetail");
        JSONArray sides = c.getJSONArray("competitors");
        for (int k = 0; k < sides.length(); k++) {
            JSONObject x = sides.getJSONObject(k);
            int score = score(x.opt("score"));
            if (id.equals(x.optString("id"))) {
                g.home = "home".equals(x.optString("homeAway"));
                g.us = score;
            } else {
                JSONObject team = x.optJSONObject("team");
                g.opp = team == null ? "" : team.optString("displayName");
                g.oppAbbr = team == null ? "" : team.optString("abbreviation");
                g.them = score;
            }
        }
        return g;
    }

    /** A score as ESPN gives it: a string, a number, or an object with its displayValue; -1 when there's none. */
    static int score(Object s) {
        if (s instanceof JSONObject) s = ((JSONObject) s).opt("displayValue");
        if (s instanceof Number) return ((Number) s).intValue();
        try {
            return s instanceof String ? Integer.parseInt((String) s) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** ESPN's F1 scoreboard: this race weekend's sessions, with the lap and the leader while one runs. */
    static Sports.Weekend weekend(JSONObject board) throws JSONException {
        Sports.Weekend w = new Sports.Weekend();
        JSONArray events = board.optJSONArray("events");
        if (events == null || events.length() == 0) return w;
        JSONObject e = events.getJSONObject(0);
        w.name = e.optString("name").replace(" Grand Prix", " GP");
        JSONArray sessions = e.optJSONArray("competitions");
        for (int i = 0; sessions != null && i < sessions.length(); i++) {
            JSONObject c = sessions.getJSONObject(i);
            Sports.Session s = new Sports.Session();
            JSONObject type = c.optJSONObject("type");
            String abbr = type == null ? "" : type.optString("abbreviation");
            s.name = abbr.startsWith("FP") ? "Practice " + abbr.substring(2) : abbr.equals("Qual") ? "Qualifying" : abbr;
            s.at = Sports.time(c.optString("date"));
            JSONObject status = c.optJSONObject("status"), st = status == null ? null : status.optJSONObject("type");
            s.state = Sports.state(st == null ? "" : st.optString("state"));
            s.lap = status == null ? 0 : status.optInt("period");
            JSONArray order = c.optJSONArray("competitors");
            for (int k = 0; order != null && k < order.length(); k++) {
                JSONObject x = order.getJSONObject(k), who = x.optJSONObject("athlete");
                if (x.optInt("order") == 1 && who != null) s.leader = who.optString("shortName");
            }
            w.sessions.add(s);
        }
        return w;
    }
}
