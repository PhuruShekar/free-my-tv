package io.freemytv;

import java.util.Calendar;

/**
 * Decisions with no Android in them, so `tools/test.sh` checks them on a computer (app/test/io/freemytv/RulesTest):
 * which sky goes with the weather, how dark it is around sunrise and sunset, the AQHI's risk, which Environment Canada
 * alert shows, and an HDMI port's number. Only java.* here.
 */
final class Rules {
    private Rules() {}

    // MARK: - Sky

    static final int CLEAR = 0, CLOUDY = 1, RAIN = 2, SNOW = 3;

    /** Open-Meteo's WMO code as a sky. */
    static int skyKind(int wmo) {
        if (wmo <= 2) return CLEAR;
        if (wmo >= 71 && wmo <= 77 || wmo == 85 || wmo == 86) return SNOW;
        if (wmo >= 51) return RAIN;   // drizzle, rain, showers, thunderstorms
        return CLOUDY;                // overcast and fog
    }

    /**
     * How dark the sky is at `now`: 0 by day, 1 by night, blended over the hour around sunrise and sunset (the half
     * hour each side). Without a forecast (0), 6:00 and 18:00. Times of day on the TV's clock.
     */
    static float skyNight(long now, long sunrise, long sunset) {
        long t = timeOfDay(now, 0);
        long rise = timeOfDay(sunrise, 6 * 3600_000L), set = timeOfDay(sunset, 18 * 3600_000L);
        float half = 30 * 60_000f;
        float dawn = smooth((t - (rise - half)) / (2 * half)), dusk = smooth((t - (set - half)) / (2 * half));
        return 1 - dawn + dusk;   // night until dawn, day until dusk, night after
    }

    /** Thunderstorms (WMO 95, 96 and 99): the rain sky with lightning. */
    static boolean isStorm(int wmo) {
        return wmo >= 95 && wmo <= 99;
    }

    /**
     * The wind's push on the rain or snow at `t` seconds, from -1 to 1: mostly a gentle sway, with now and then a
     * stronger gust (three slow waves that line up only at times, so it never repeats in a way the eye catches).
     */
    static float gust(double t) {
        double sway = Math.sin(t * 0.21) * 0.55 + Math.sin(t * 0.53 + 2) * 0.3 + Math.sin(t * 1.7 + 1) * 0.08;
        double burst = Math.max(0, Math.sin(t * 0.071 + 0.5));
        return (float) (sway * (0.45 + 0.55 * burst * burst));
    }

    /**
     * A lightning flash `t` seconds after it starts, from 0 to 1: a sharp strike, a quick dip, a second weaker flicker,
     * then it fades out by 0.6 s (as lightning does: several return strokes in one flash).
     */
    static float flash(float t) {
        if (t <= 0 || t >= 0.6f) return 0;
        if (t < 0.05f) return t / 0.05f;
        if (t < 0.12f) return 1 - (t - 0.05f) / 0.07f * 0.8f;
        if (t < 0.16f) return 0.2f + (t - 0.12f) / 0.04f * 0.5f;
        return 0.7f * (1 - (t - 0.16f) / 0.44f);
    }

    /** Milliseconds since midnight on the TV's clock; `fallback` for 0 (no data). */
    static long timeOfDay(long at, long fallback) {
        if (at == 0) return fallback;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return ((c.get(Calendar.HOUR_OF_DAY) * 60L + c.get(Calendar.MINUTE)) * 60 + c.get(Calendar.SECOND)) * 1000L;
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // MARK: - Air quality

    /** The AQHI's risk: 0 low (1 to 3), 1 moderate (4 to 6), 2 high (7 to 10), 3 very high (above 10). */
    static int aqhiRisk(double aqhi) {
        long v = Math.round(aqhi);
        return v <= 3 ? 0 : v <= 6 ? 1 : v <= 10 ? 2 : 3;
    }

    /** As Environment Canada writes it: rounded, at least 1, "10+" above 10. */
    static String aqhiNumber(double aqhi) {
        long v = Math.max(1, Math.round(aqhi));
        return v > 10 ? "10+" : String.valueOf(v);
    }

    // MARK: - Alerts

    static final int ADVISORY = 0, WATCH = 1, WARNING = 2;

    /** The alert's level from Environment Canada's `alert_type`; -1 for what doesn't show (statements, the rest). */
    static int alertLevel(String type) {
        switch (type) {
            case "warning": return WARNING;
            case "watch": return WATCH;
            case "advisory": return ADVISORY;
            default: return -1;
        }
    }

    /** Still on at `now`: not ended or cancelled, and neither its end nor its expiry has passed (0 = not given). */
    static boolean alertActive(String status, long ends, long expires, long now) {
        if (status.equals("ended") || status.equals("cancelled")) return false;
        return (ends == 0 || ends > now) && (expires == 0 || expires > now);
    }

    /** Higher shows first: warning over watch over advisory, then red over orange over yellow. */
    static int alertRank(int level, String colour) {
        return level * 3 + (colour.equals("red") ? 2 : colour.equals("orange") ? 1 : 0);
    }

    /** Environment Canada's risk colour on screen (yellow when it gives none). */
    static int alertColour(String colour) {
        return colour.equals("red") ? 0xFFE5484D : colour.equals("orange") ? 0xFFF5821F : 0xFFF5C518;
    }

    /** "rainfall warning" → "Rainfall warning". */
    static String sentence(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // MARK: - Inputs

    /** The Fire TV Stick's port: first on the shelf, a wide tile, never moved or removed (HomeApps.fireTv). */
    static final String FIRE_TV_PORT = "3";

    /** An HDMI port's number from its label ("HDMI 3" → 3), else `fallback` (its place in the list). */
    static int portNumber(String label, int fallback) {
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch >= '0' && ch <= '9') return ch - '0';
        }
        return fallback;
    }
}
