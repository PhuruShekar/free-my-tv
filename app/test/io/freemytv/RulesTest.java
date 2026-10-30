package io.freemytv;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Checks Rules on a computer, without a TV or Android: `tools/test.sh`. No libraries, so no Gradle: each check
 * prints what failed and the run exits with 1.
 */
public final class RulesTest {
    private static int failed, passed;

    public static void main(String[] args) {
        // The owner's TV: Burnaby's clock, so times of day read as on the TV.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
        skyKind();
        skyNight();
        wind();
        airQuality();
        alerts();
        ports();
        board();
        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        if (failed > 0) System.exit(1);
    }

    private static void skyKind() {
        eq("clear sky", Rules.CLEAR, Rules.skyKind(0));
        eq("partly cloudy is still clear", Rules.CLEAR, Rules.skyKind(2));
        eq("overcast", Rules.CLOUDY, Rules.skyKind(3));
        eq("fog", Rules.CLOUDY, Rules.skyKind(45));
        eq("drizzle", Rules.RAIN, Rules.skyKind(51));
        eq("rain", Rules.RAIN, Rules.skyKind(63));
        eq("freezing rain", Rules.RAIN, Rules.skyKind(67));
        eq("rain showers", Rules.RAIN, Rules.skyKind(81));
        eq("thunderstorm", Rules.RAIN, Rules.skyKind(95));
        eq("snow", Rules.SNOW, Rules.skyKind(73));
        eq("snow grains", Rules.SNOW, Rules.skyKind(77));
        eq("snow showers", Rules.SNOW, Rules.skyKind(86));
        check("thunderstorms have lightning", Rules.isStorm(95) && Rules.isStorm(96) && Rules.isStorm(99));
        check("rain alone doesn't", !Rules.isStorm(63) && !Rules.isStorm(82));
    }

    private static void wind() {
        float most = 0, least = 0;
        boolean jumps = false;
        float last = Rules.gust(0);
        for (int i = 1; i <= 36000; i++) {   // an hour, at 10 steps a second
            float g = Rules.gust(i / 10.0);
            most = Math.max(most, g);
            least = Math.min(least, g);
            if (Math.abs(g - last) > 0.05f) jumps = true;
            last = g;
        }
        check("gusts stay within -1 and 1", most <= 1 && least >= -1);
        check("gusts blow both ways", most > 0.4f && least < -0.4f);
        check("gusts change smoothly (no jumps from one frame to the next)", !jumps);
        near("no flash before it starts", 0, Rules.flash(0));
        near("the strike peaks at 50 ms", 1, Rules.flash(0.05f));
        check("then dips", Rules.flash(0.12f) < 0.3f);
        check("then flickers again, weaker", Rules.flash(0.16f) > 0.5f && Rules.flash(0.16f) < 1);
        near("gone by 0.6 s", 0, Rules.flash(0.6f));
        boolean inside = true;
        for (int i = 0; i <= 70; i++) inside &= Rules.flash(i / 100f) >= 0 && Rules.flash(i / 100f) <= 1;
        check("a flash stays between 0 and 1", inside);
    }

    private static void skyNight() {
        long rise = at(7, 55), set = at(17, 55);
        near("noon is day", 0, Rules.skyNight(at(12, 0), rise, set));
        near("late evening is night", 1, Rules.skyNight(at(23, 0), rise, set));
        near("before dawn is night", 1, Rules.skyNight(at(3, 0), rise, set));
        near("half an hour before sunset is still day", 0, Rules.skyNight(at(17, 25), rise, set));
        near("sunset is halfway", 0.5f, Rules.skyNight(set, rise, set));
        near("half an hour after sunset is night", 1, Rules.skyNight(at(18, 25), rise, set));
        near("sunrise is halfway", 0.5f, Rules.skyNight(rise, rise, set));
        float early = Rules.skyNight(at(17, 40), rise, set), late = Rules.skyNight(at(18, 10), rise, set);
        check("the evening darkens step by step", 0 < early && early < 0.5f && 0.5f < late && late < 1);
        near("no forecast: noon is day", 0, Rules.skyNight(at(12, 0), 0, 0));
        near("no forecast: 8 pm is night", 1, Rules.skyNight(at(20, 0), 0, 0));
    }

    private static void airQuality() {
        eq("1.81 is low", 0, Rules.aqhiRisk(1.81));
        eq("3.4 rounds to 3: low", 0, Rules.aqhiRisk(3.4));
        eq("3.5 rounds to 4: moderate", 1, Rules.aqhiRisk(3.5));
        eq("6 is moderate", 1, Rules.aqhiRisk(6));
        eq("7 is high", 2, Rules.aqhiRisk(7));
        eq("10 is high", 2, Rules.aqhiRisk(10));
        eq("10.6 is very high", 3, Rules.aqhiRisk(10.6));
        eq("written rounded", "2", Rules.aqhiNumber(1.81));
        eq("never below 1", "1", Rules.aqhiNumber(0.2));
        eq("above 10 is 10+", "10+", Rules.aqhiNumber(11));
    }

    private static void alerts() {
        long now = at(14, 0);
        eq("warning", Rules.WARNING, Rules.alertLevel("warning"));
        eq("watch", Rules.WATCH, Rules.alertLevel("watch"));
        eq("advisory", Rules.ADVISORY, Rules.alertLevel("advisory"));
        eq("statements don't show", -1, Rules.alertLevel("statement"));
        eq("nor anything unknown", -1, Rules.alertLevel(""));
        check("issued and still on", Rules.alertActive("issued", now + 3600_000, now + 7200_000, now));
        check("continued, no end given", Rules.alertActive("continued", 0, 0, now));
        check("ended doesn't show", !Rules.alertActive("ended", now + 3600_000, 0, now));
        check("cancelled doesn't show", !Rules.alertActive("cancelled", 0, 0, now));
        check("past its end doesn't show", !Rules.alertActive("issued", now - 60_000, 0, now));
        check("past its expiry doesn't show", !Rules.alertActive("issued", 0, now - 60_000, now));
        check("a yellow warning beats a red watch",
                Rules.alertRank(Rules.WARNING, "yellow") > Rules.alertRank(Rules.WATCH, "red"));
        check("a watch beats an advisory", Rules.alertRank(Rules.WATCH, "yellow") > Rules.alertRank(Rules.ADVISORY, "red"));
        check("red beats orange", Rules.alertRank(Rules.WARNING, "red") > Rules.alertRank(Rules.WARNING, "orange"));
        check("orange beats yellow", Rules.alertRank(Rules.WARNING, "orange") > Rules.alertRank(Rules.WARNING, "yellow"));
        eq("yellow when none is given", Rules.alertColour("yellow"), Rules.alertColour(""));
        eq("name as a sentence", "Rainfall warning", Rules.sentence("rainfall warning"));
    }

    private static void ports() {
        eq("HDMI 3 is 3 (the Fire TV Stick's)", 3, Rules.portNumber("HDMI 3", 1));
        eq("HDMI3 is 3", 3, Rules.portNumber("HDMI3", 1));
        eq("no number: its place in the list", 2, Rules.portNumber("Fire TV", 2));
        check("the Fire TV tile is on HDMI 3", Rules.FIRE_TV_PORT.equals("3"));
        check("the JBL soundbar is sound only", Rules.soundOnly("JBL BAR 5.1"));
        check("the Fire TV Stick isn't", !Rules.soundOnly("Fire TV Stick"));
        check("nor a Switch", !Rules.soundOnly("Nintendo Switch"));
        eq("the stick's name is kept", "Fire TV Stick", Rules.portDevice(" Fire TV Stick ", "3"));
        eq("the soundbar alone (stick asleep) names nothing", "", Rules.portDevice("JBL BAR 5.1", "3"));
        eq("the port's own label names nothing", "", Rules.portDevice("HDMI 3", "3"));
        eq("nor HDMI3", "", Rules.portDevice("hdmi3", "3"));
        String[] ports = {"1", "2", "3"};
        eq("HDMI 3 on: it wins", 2, Rules.shelfPort(ports, new boolean[] {true, true, true}));
        eq("HDMI 3 off, 1 on: HDMI 1", 0, Rules.shelfPort(ports, new boolean[] {true, true, false}));
        eq("only HDMI 2 on: HDMI 2", 1, Rules.shelfPort(ports, new boolean[] {false, true, false}));
        eq("nothing on: HDMI 3 anyway", 2, Rules.shelfPort(ports, new boolean[] {false, false, false}));
        eq("no HDMI 3 and nothing on: none", -1, Rules.shelfPort(new String[] {"1"}, new boolean[] {false}));
        eq("the stick's CEC name: fire tv", "fire tv", Rules.portTileName("Fire TV Stick", "3"));
        eq("a Switch", "Switch", Rules.portTileName("Nintendo Switch", "3"));
        eq("a Switch named by CEC as NINTENDO", Rules.TILE_SWITCH, Rules.portTile("NINTENDO"));
        eq("anything else keeps its name", "PlayStation 5", Rules.portTileName("PlayStation 5", "3"));
        eq("nothing named: the port", "HDMI 3", Rules.portTileName(" ", "3"));
        eq("the port's own label is other", Rules.TILE_OTHER, Rules.portTile("HDMI 3"));
    }

    private static void board() {
        check("9 pm is quiet", Rules.quietHours(21));
        check("midnight is quiet", Rules.quietHours(0));
        check("5 am is quiet", Rules.quietHours(5));
        check("6 am isn't", !Rules.quietHours(6));
        check("8 pm isn't", !Rules.quietHours(20));
        eq("7 am: the bus timelines", Rules.BUS_TIMELINES, Rules.busView(7));
        eq("11 am: the bus line", Rules.BUS_LINE, Rules.busView(11));
        eq("3 pm: the bus line", Rules.BUS_LINE, Rules.busView(15));
        eq("4 pm: the timelines again", Rules.BUS_TIMELINES, Rules.busView(16));
        eq("9 pm: quiet", Rules.BUS_QUIET, Rules.busView(21));
        int C = Rules.CLEAR, R = Rules.RAIN, S = Rules.SNOW, D = Rules.CLOUDY;
        int[] from = Rules.precipChange(D, new int[] {D, D, R, R});
        check("dry now, rain in 2 hours: rain from then", from[0] == Rules.STARTS && from[1] == 2 && from[2] == R);
        int[] until = Rules.precipChange(R, new int[] {R, D, D});
        check("raining, dry in 1 hour: rain until then", until[0] == Rules.STOPS && until[1] == 1 && until[2] == R);
        check("snow stopping says snow", Rules.precipChange(S, new int[] {C})[2] == S);
        eq("dry all along: nothing to say", Rules.NO_CHANGE, Rules.precipChange(C, new int[] {C, D, C})[0]);
        eq("wet all along: nothing to say", Rules.NO_CHANGE, Rules.precipChange(R, new int[] {R, S})[0]);
        eq("no hours: nothing to say", Rules.NO_CHANGE, Rules.precipChange(R, new int[0])[0]);
        // The TV's row as Android lists it: Live TV, HDMI 1, HDMI 2, HDMI 3, with the Fire TV Stick on HDMI 3.
        int on = Rules.PORT_ON, live = Rules.LIVE_TV, empty = Rules.PORT_EMPTY;
        eq("sources: HDMI 3, Live TV, HDMI 1, HDMI 2", "[3, 0, 1, 2]",
                java.util.Arrays.toString(Rules.sourceOrder(new int[] {live, empty, empty, on})));
        eq("sources: ports with devices keep their order", "[1, 2, 0]",
                java.util.Arrays.toString(Rules.sourceOrder(new int[] {live, on, on})));
    }

    /** Today at `hour`:`minute` on the TV's clock. */
    private static long at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.OCTOBER, 29, hour, minute, 0);
        c.set(Calendar.MILLISECOND, 0);
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

    private static void near(String what, float want, float got) {
        check(what + " (wanted " + want + ", got " + got + ")", Math.abs(want - got) < 0.01f);
    }
}
