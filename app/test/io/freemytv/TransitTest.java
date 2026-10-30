package io.freemytv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

/** Checks Transit on a computer with the real timetable (app/assets/buses.txt): `tools/test.sh`. */
public final class TransitTest {
    private static int failed, passed;
    private static final HashSet<String> STOPS = new HashSet<>(java.util.Arrays.asList("2476", "2430", "2478"));

    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
        Transit.Table t = Transit.parse(new String(Files.readAllBytes(Paths.get("app/assets/buses.txt")), StandardCharsets.UTF_8));
        eq("four bus and stop pairs", 4, t.pairs.size());
        eq("a Friday runs weekday buses", Transit.WEEKDAY, Transit.dayType(t, 20261030, Calendar.FRIDAY));
        eq("Saturday", Transit.SATURDAY, Transit.dayType(t, 20261031, Calendar.SATURDAY));
        eq("Remembrance Day (a Wednesday) runs Sunday buses", Transit.SUNDAY, Transit.dayType(t, 20261111, Calendar.WEDNESDAY));
        eq("after the timetable ends: none", -1, Transit.dayType(t, 20270104, Calendar.MONDAY));
        check("still valid in October", !Transit.expired(t, at(30, 7, 45)));

        long now = at(30, 7, 45);   // Friday
        List<Transit.Bus> e = Transit.next(t, "Edmonds Station", null, now, 3);
        eq("three buses to Edmonds", 3, e.size());
        boolean sorted = true, reachable = true;
        for (int i = 0; i < e.size(); i++) {
            reachable &= e.get(i).leave >= now;
            if (i > 0) sorted &= e.get(i).leave >= e.get(i - 1).leave;
        }
        check("all leave by now or later", reachable);
        check("soonest first", sorted);
        check("none live without the feed", !e.get(0).live);
        Transit.Bus first = e.get(0);
        long walk = first.route.equals("106") ? 4 : 8;
        eq("leave = departure minus the walk", walk * 60_000L, first.depart - first.leave);
        check("arrives after it departs", first.arrive > first.depart);
        eq("Metrotown is the 119", "119", Transit.next(t, "Metrotown", null, now, 3).get(0).route);
        check("after midnight: yesterday's late buses", !Transit.next(t, "Edmonds Station", null, at(31, 0, 30), 3).isEmpty());

        // A live departure 2 minutes late for that first bus, as TransLink sends it.
        long late = first.depart + 120_000L;
        String stop = first.route.equals("106") ? "2476" : "2430", route = first.route.equals("106") ? "6645" : first.route.equals("119") ? "32799" : "33292";
        Transit.Feed feed = Transit.decode(message(now, route, stop, late, 120), STOPS);
        eq("the feed's time", now, feed.at);
        eq("one departure at our stops", 1, feed.departures.size());
        eq("its route", route, feed.departures.get(0).routeId);
        List<Transit.Bus> live = Transit.next(t, "Edmonds Station", feed, now + 60_000L, 3);
        Transit.Bus moved = null;
        for (Transit.Bus b : live) if (b.live) moved = b;
        check("the late bus is live", moved != null);
        if (moved != null) {
            eq("at its live time", late, moved.depart);
            eq("its arrival moved with it", first.arrive + 120_000L, moved.arrive);
        }
        // The same feed 16 minutes on, for a bus that leaves after that: too old, so the timetable's time again.
        Transit.Bus later = Transit.next(t, "Edmonds Station", null, now + 16 * 60_000L, 1).get(0);
        String laterStop = later.route.equals("106") ? "2476" : "2430", laterRoute = later.route.equals("106") ? "6645" : later.route.equals("119") ? "32799" : "33292";
        Transit.Feed old = Transit.decode(message(now, laterRoute, laterStop, later.depart + 60_000L, 60), STOPS);
        check("the feed while fresh makes it live", Transit.next(t, "Edmonds Station", old, now + 60_000L, 9).stream().anyMatch(b -> b.live));
        check("live data 16 minutes old: timetable only", Transit.next(t, "Edmonds Station", old, now + 16 * 60_000L, 9).stream().noneMatch(b -> b.live));
        // A live bus 6 minutes late isn't taken for the next one in the timetable: it joins as its own.
        Transit.Feed far = Transit.decode(message(now, route, stop, first.depart + 6 * 60_000L, 0), STOPS);
        far.departures.get(0).hasDelay = false;
        long kept = Transit.next(t, "Edmonds Station", far, now, 9).stream().filter(b -> b.depart == first.depart && !b.live).count();
        check("6 minutes off: the timetable bus stays", kept == 1);
        eq("a stop we don't use is left out", 0, Transit.decode(message(now, route, "9999", late, 0), STOPS).departures.size());
        boolean threw = false;
        try {
            Transit.decode(new byte[] {0x12, 0x7F, 0x01}, STOPS);
        } catch (IllegalArgumentException x) {
            threw = true;
        }
        check("a cut-off message throws", threw);

        eq("under a minute is 0 (leave now)", 0, Transit.minutesTo(now + 59_000L, now));
        eq("4 min 30 s is 4", 4, Transit.minutesTo(now + 270_000L, now));
        check("45 minutes ahead is the timeline's end", Transit.onTimeline(now + 45 * 60_000L, now) == 1);

        // A real feed saved from TransLink, when given: `java -cp out/test io.freemytv.TransitTest feed.pb`.
        if (args.length > 0) {
            Transit.Feed real = Transit.decode(Files.readAllBytes(Paths.get(args[0])), STOPS);
            System.out.println("real feed: " + real.departures.size() + " departures at our stops, at " + new java.util.Date(real.at));
            for (Transit.Live l : real.departures) System.out.println("  route " + l.routeId + " stop " + l.stopId + " " + new java.util.Date(l.time) + (l.hasDelay ? " delay " + l.delay + " s" : ""));
            for (String dest : new String[] {"Edmonds Station", "Metrotown"}) {
                for (Transit.Bus b : Transit.next(t, dest, real, real.at, 3)) {
                    System.out.println("  " + dest + " " + b.route + " leave " + new java.util.Date(b.leave) + (b.live ? " live" : " timetable"));
                }
            }
        }
        System.out.println(passed + " checks passed" + (failed > 0 ? ", " + failed + " failed" : ""));
        if (failed > 0) System.exit(1);
    }

    /** A FeedMessage with one trip and one stop, encoded by hand. */
    private static byte[] message(long at, String route, String stop, long time, int delay) {
        byte[] event = concat(field(1, varint(delay)), field(2, varint(time / 1000)));
        byte[] stopTime = concat(bytes(3, event), bytes(4, stop.getBytes(StandardCharsets.UTF_8)));
        byte[] trip = concat(bytes(2, stopTime), bytes(1, bytes(5, route.getBytes(StandardCharsets.UTF_8))));
        byte[] entity = concat(bytes(1, "1".getBytes(StandardCharsets.UTF_8)), bytes(3, trip));
        return concat(bytes(1, field(3, varint(at / 1000))), bytes(2, entity));
    }

    private static byte[] varint(long v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        while ((v & ~0x7FL) != 0) {
            o.write((int) (v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write((int) v);
        return o.toByteArray();
    }

    private static byte[] field(int n, byte[] varint) { return concat(varint(n << 3), varint); }

    private static byte[] bytes(int n, byte[] b) { return concat(varint(n << 3 | 2), varint(b.length), b); }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    /** October `day`, 2026, at `hour`:`minute` on the TV's clock. */
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
