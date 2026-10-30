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

    static final int NO_CHANGE = 0, STARTS = 1, STOPS = 2;

    /**
     * The weather's one sentence: whether rain or snow starts (STARTS) or stops (STOPS) within the next `hours` kinds
     * (skyKind of each coming hour), and at which hour (the result's second value); NO_CHANGE when neither.
     * The first value is the change, the third the kind (RAIN or SNOW) it's about.
     */
    static int[] precipChange(int nowKind, int[] hours) {
        boolean wet = nowKind == RAIN || nowKind == SNOW;
        for (int i = 0; i < hours.length; i++) {
            boolean w = hours[i] == RAIN || hours[i] == SNOW;
            if (wet && !w) return new int[] {STOPS, i, nowKind};
            if (!wet && w) return new int[] {STARTS, i, hours[i]};
        }
        return new int[] {NO_CHANGE, -1, nowKind};
    }

    /** Quiet hours, 9 pm to 6 am: the board dims and the sky stays still. */
    static boolean quietHours(int hour) {
        return hour >= 21 || hour < 6;
    }

    static final int BUS_TIMELINES = 0, BUS_LINE = 1, BUS_QUIET = 2;

    /**
     * How the main screen shows the buses at `hour`: the two timelines in the morning (6 to 11) and the evening (4 to
     * 9 pm), one line over the shelf in the day, and in quiet hours one bright line, the timelines one press down.
     */
    static int busView(int hour) {
        if (quietHours(hour)) return BUS_QUIET;
        return hour >= 11 && hour < 16 ? BUS_LINE : BUS_TIMELINES;
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

    static final int PORT_ON = 0, LIVE_TV = 1, PORT_EMPTY = 2;

    /**
     * The TV sources row's order: ports with a device on them first (the Fire TV Stick), then Live TV, then the empty
     * ports, each keeping its own order. `rank` is PORT_ON, LIVE_TV or PORT_EMPTY per source; returns their indexes.
     */
    static int[] sourceOrder(int[] rank) {
        int[] order = new int[rank.length];
        int n = 0;
        for (int r = PORT_ON; r <= PORT_EMPTY; r++) {
            for (int i = 0; i < rank.length; i++) if (rank[i] == r) order[n++] = i;
        }
        return order;
    }

    /** The ports the shelf's first tile can show, best first: the Fire TV Stick's, then HDMI 1, then HDMI 2. */
    static final String[] SHELF_PORTS = {FIRE_TV_PORT, "1", "2"};

    /**
     * Which port the shelf's first tile shows: the first in SHELF_PORTS with a device on it (`on`), else HDMI 3 even
     * with nothing on it (OK can still wake the stick). `numbers` are the ports' numbers; returns an index, or -1.
     */
    static int shelfPort(String[] numbers, boolean[] on) {
        for (String want : SHELF_PORTS) {
            for (int i = 0; i < numbers.length; i++) if (want.equals(numbers[i]) && on[i]) return i;
        }
        for (int i = 0; i < numbers.length; i++) if (FIRE_TV_PORT.equals(numbers[i])) return i;
        return -1;
    }

    /** An HDMI-CEC name that's a soundbar or receiver (on the port for ARC), not something you watch. */
    static boolean soundOnly(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String w : new String[] {"bar", "sound", "audio", "receiver", "avr", "sonos", "speaker", "theater", "theatre"}) {
            if (n.contains(w)) return true;
        }
        return false;
    }

    /**
     * The device a port's label names, to show and remember; "" when it names none: the port's own "HDMI 3", or a
     * soundbar alone on it (the stick on that port asleep: its saved name stands, the soundbar's isn't saved over it).
     */
    static String portDevice(String label, String port) {
        String name = label.trim();
        boolean bare = name.equalsIgnoreCase("HDMI " + port) || name.equalsIgnoreCase("HDMI" + port);
        return bare || soundOnly(name) ? "" : name;
    }

    static final int TILE_FIRE = 0, TILE_SWITCH = 1, TILE_OTHER = 2;

    /** How the shelf's HDMI 3 tile looks, from the name of what's on the port (HDMI-CEC's, or yours). */
    static int portTile(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("fire")) return TILE_FIRE;
        if (n.contains("switch") || n.contains("nintendo")) return TILE_SWITCH;
        return TILE_OTHER;
    }

    /** The tile's word: "fire tv" (lowercase, like a wordmark), "Switch", else the device's name or "HDMI 3". */
    static String portTileName(String name, String port) {
        switch (portTile(name)) {
            case TILE_FIRE: return "fire tv";
            case TILE_SWITCH: return "Switch";
            default: return name.trim().isEmpty() ? "HDMI " + port : name.trim();
        }
    }

    /** An HDMI port's number from its label ("HDMI 3" → 3), else `fallback` (its place in the list). */
    static int portNumber(String label, int fallback) {
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch >= '0' && ch <= '9') return ch - '0';
        }
        return fallback;
    }

    /** A day (given by its noon at the city) that is over: its last hour has passed. Today and later stay in the forecast. */
    static boolean dayEnded(long noon, long now) {
        return noon + 12 * 3600_000L <= now;
    }

    /** Where `v` falls between `lo` and `hi`, 0 to 1 (the middle when they are the same): a day's bar on the week's scale. */
    static float share(double v, double lo, double hi) {
        return hi <= lo ? 0.5f : (float) Math.max(0, Math.min(1, (v - lo) / (hi - lo)));
    }
}
