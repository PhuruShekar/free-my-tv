package io.freemytv;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The board's buses with no Android in them, so `tools/test.sh` checks them on a computer: the built-in timetable
 * (assets/buses.txt, from tools/BusTimetable.java), TransLink's live feed (GTFS-Realtime, a protobuf, read by hand:
 * no libraries) and which buses show. Only java.* here.
 */
final class Transit {
    private Transit() {}

    /** Live data older than this counts as gone: every time shows as the timetable's. */
    static final long LIVE_FOR = 15 * 60_000L;
    /** The timeline's width: buses this far ahead. */
    static final int WINDOW_MIN = 45;
    static final int WEEKDAY = 0, SATURDAY = 1, SUNDAY = 2;

    /** One bus at one stop, as the timetable has it (minutes after the service day's midnight; past 1440 after midnight). */
    static final class Pair {
        String dest, route, stop, routeId, stopId;
        int walk;
        final int[][] depart = new int[3][], arrive = new int[3][];
    }

    static final class Table {
        int from, to;   // yyyymmdd, the days it's valid
        final Map<Integer, Integer> days = new HashMap<>();   // yyyymmdd → the day type it runs instead
        final List<Pair> pairs = new ArrayList<>();
    }

    static Table parse(String text) {
        Table t = new Table();
        Pair p = null;
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split(" ", 2);
            String rest = f.length > 1 ? f[1] : "";
            switch (f[0]) {
                case "valid": {
                    String[] v = rest.split(" ");
                    t.from = Integer.parseInt(v[0]);
                    t.to = Integer.parseInt(v[1]);
                    break;
                }
                case "day": {
                    String[] v = rest.split(" ");
                    t.days.put(Integer.parseInt(v[0]), "wsu".indexOf(v[1].charAt(0)));
                    break;
                }
                case "pair": {
                    String[] v = rest.split("\\|");
                    p = new Pair();
                    p.dest = v[0];
                    p.route = v[1];
                    p.stop = v[2];
                    p.walk = Integer.parseInt(v[3]);
                    p.routeId = v[4];
                    p.stopId = v[5];
                    t.pairs.add(p);
                    break;
                }
                default: {
                    int day = "wsu".indexOf(f[0]);
                    if (p == null || day < 0) break;
                    String[] v = rest.isEmpty() ? new String[0] : rest.split(" ");
                    p.depart[day] = new int[v.length];
                    p.arrive[day] = new int[v.length];
                    for (int i = 0; i < v.length; i++) {
                        int dash = v[i].indexOf('-');
                        p.depart[day][i] = Integer.parseInt(v[i].substring(0, dash));
                        p.arrive[day][i] = Integer.parseInt(v[i].substring(dash + 1));
                    }
                }
            }
        }
        return t;
    }

    /** Which buses run on `date` (yyyymmdd, `weekday` as Calendar's): a holiday's, else the day's own. -1 off the table. */
    static int dayType(Table t, int date, int weekday) {
        if (date < t.from || date > t.to) return -1;
        Integer d = t.days.get(date);
        if (d != null) return d;
        return weekday == Calendar.SATURDAY ? SATURDAY : weekday == Calendar.SUNDAY ? SUNDAY : WEEKDAY;
    }

    /** The timetable has run out (TransLink publishes a new one every few months): live times only. */
    static boolean expired(Table t, long now) {
        return date(midnight(now, 0)) > t.to;
    }

    /** A live departure at one of our stops. */
    static final class Live {
        String routeId, stopId;
        long time;          // epoch ms
        int delay;          // seconds late (negative: early)
        boolean hasDelay;
    }

    /** The feed's time (header timestamp, epoch ms) and its departures at `stops` (the feed's stop ids). */
    static final class Feed {
        long at;
        final List<Live> departures = new ArrayList<>();
    }

    // GTFS-Realtime's field numbers (gtfs-realtime.proto).
    private static final int MSG_HEADER = 1, MSG_ENTITY = 2, HEADER_TIME = 3, ENTITY_TRIP = 3, TRIP_DESCRIPTOR = 1,
            TRIP_STOP = 2, DESCRIPTOR_ROUTE = 5, STOP_ARRIVAL = 2, STOP_DEPARTURE = 3, STOP_ID = 4, STOP_RELATION = 5,
            EVENT_DELAY = 1, EVENT_TIME = 2, SKIPPED = 1;

    /** Reads a FeedMessage, keeping only the departures at `stops`. Throws on a broken message. */
    static Feed decode(byte[] b, java.util.Set<String> stops) {
        Feed feed = new Feed();
        Reader r = new Reader(b, 0, b.length);
        while (r.more()) {
            int key = r.varint32();
            if (key >>> 3 == MSG_HEADER && (key & 7) == 2) {
                Reader h = r.sub();
                while (h.more()) {
                    int k = h.varint32();
                    if (k >>> 3 == HEADER_TIME && (k & 7) == 0) feed.at = h.varint() * 1000; else h.skip(k);
                }
            } else if (key >>> 3 == MSG_ENTITY && (key & 7) == 2) {
                Reader e = r.sub();
                while (e.more()) {
                    int k = e.varint32();
                    if (k >>> 3 == ENTITY_TRIP && (k & 7) == 2) tripUpdate(e.sub(), stops, feed.departures); else e.skip(k);
                }
            } else {
                r.skip(key);
            }
        }
        return feed;
    }

    private static void tripUpdate(Reader t, java.util.Set<String> stops, List<Live> out) {
        String route = "";
        int first = out.size();
        while (t.more()) {
            int k = t.varint32();
            if (k >>> 3 == TRIP_DESCRIPTOR && (k & 7) == 2) {
                Reader d = t.sub();
                while (d.more()) {
                    int dk = d.varint32();
                    if (dk >>> 3 == DESCRIPTOR_ROUTE && (dk & 7) == 2) route = d.string(); else d.skip(dk);
                }
            } else if (k >>> 3 == TRIP_STOP && (k & 7) == 2) {
                Live l = stopTime(t.sub(), stops);
                if (l != null) out.add(l);
            } else {
                t.skip(k);
            }
        }
        // The descriptor can come after the stops: name the route once the whole trip is read.
        for (int i = first; i < out.size(); i++) out.get(i).routeId = route;
    }

    private static Live stopTime(Reader s, java.util.Set<String> stops) {
        String stop = null;
        long arrival = 0, departure = 0;
        int delay = 0;
        boolean hasDelay = false, skipped = false;
        while (s.more()) {
            int k = s.varint32();
            int field = k >>> 3;
            if ((field == STOP_ARRIVAL || field == STOP_DEPARTURE) && (k & 7) == 2) {
                Reader e = s.sub();
                long time = 0;
                while (e.more()) {
                    int ek = e.varint32();
                    if (ek >>> 3 == EVENT_TIME && (ek & 7) == 0) {
                        time = e.varint() * 1000;
                    } else if (ek >>> 3 == EVENT_DELAY && (ek & 7) == 0) {
                        delay = (int) e.varint();
                        hasDelay = true;
                    } else {
                        e.skip(ek);
                    }
                }
                if (field == STOP_ARRIVAL) arrival = time; else departure = time;
            } else if (field == STOP_ID && (k & 7) == 2) {
                stop = s.string();
            } else if (field == STOP_RELATION && (k & 7) == 0) {
                skipped = s.varint() == SKIPPED;
            } else {
                s.skip(k);
            }
        }
        if (stop == null || skipped || !stops.contains(stop)) return null;
        long time = departure != 0 ? departure : arrival;
        if (time == 0) return null;
        Live l = new Live();
        l.stopId = stop;
        l.time = time;
        l.delay = delay;
        l.hasDelay = hasDelay;
        return l;
    }

    /** Protobuf's wire format, over a slice of the message. */
    private static final class Reader {
        final byte[] b;
        int at;
        final int end;

        Reader(byte[] b, int at, int end) {
            this.b = b;
            this.at = at;
            this.end = end;
        }

        boolean more() { return at < end; }

        long varint() {
            long v = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (at >= end) throw new IllegalArgumentException("varint past the end");
                byte x = b[at++];
                v |= (long) (x & 0x7F) << shift;
                if (x >= 0) return v;
            }
            throw new IllegalArgumentException("varint too long");
        }

        int varint32() { return (int) varint(); }

        Reader sub() {
            int n = varint32();
            if (n < 0 || at + n > end) throw new IllegalArgumentException("field past the end");
            Reader r = new Reader(b, at, at + n);
            at += n;
            return r;
        }

        String string() {
            Reader r = sub();
            return new String(b, r.at, r.end - r.at, java.nio.charset.StandardCharsets.UTF_8);
        }

        void skip(int key) {
            switch (key & 7) {
                case 0: varint(); break;
                case 1: at += 8; break;
                case 2: sub(); break;
                case 5: at += 4; break;
                default: throw new IllegalArgumentException("wire type " + (key & 7));
            }
            if (at > end) throw new IllegalArgumentException("field past the end");
        }
    }

    /** A bus to show. */
    static final class Bus {
        String route;
        long leave, depart, arrive;   // epoch ms; leave = depart minus the walk; arrive 0 = unknown
        boolean live;                 // from the live feed (else the timetable's)
    }

    /**
     * The next `n` buses to `dest` you can still walk to (leave by now or later), soonest first. The timetable's,
     * with each one the live feed has replaced by its live time; `feed` is null (or older than LIVE_FOR) without live data.
     */
    static List<Bus> next(Table t, String dest, Feed feed, long now, int n) {
        boolean live = feed != null && now - feed.at < LIVE_FOR && now >= feed.at - LIVE_FOR;
        List<Bus> all = new ArrayList<>();
        for (Pair p : t.pairs) {
            if (!p.dest.equals(dest)) continue;
            List<Bus> planned = scheduled(t, p, now);
            if (live) {
                for (Live l : feed.departures) {
                    if (l.routeId.equals(p.routeId) && l.stopId.equals(p.stopId)) match(planned, l, p.route);
                }
            }
            for (Bus b : planned) {
                b.leave = b.depart - p.walk * 60_000L;
                if (b.leave >= now) all.add(b);
            }
        }
        all.sort((a, b) -> Long.compare(a.leave, b.leave));
        return all.size() > n ? new ArrayList<>(all.subList(0, n)) : all;
    }

    /** The timetable's buses at `p` from yesterday's service (past midnight) and today's, from an hour ago on. */
    private static List<Bus> scheduled(Table t, Pair p, long now) {
        List<Bus> out = new ArrayList<>();
        for (int back = 1; back >= 0; back--) {
            long day = midnight(now, -back);
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(day);
            int type = dayType(t, date(day), c.get(Calendar.DAY_OF_WEEK));
            if (type < 0 || p.depart[type] == null) continue;
            for (int i = 0; i < p.depart[type].length; i++) {
                // A cheap look first (an hour wider, for a DST day): most of the day's buses are hours away.
                long rough = day + p.depart[type][i] * 60_000L;
                if (rough < now - 2 * 3600_000L || rough > now + 4 * 3600_000L) continue;
                long depart = at(day, p.depart[type][i]);
                if (depart < now - 3600_000L || depart > now + 3 * 3600_000L) continue;
                Bus b = new Bus();
                b.route = p.route;
                b.depart = depart;
                b.arrive = at(day, p.arrive[type][i]);
                out.add(b);
            }
        }
        return out;
    }

    /**
     * Puts a live departure on its bus in the timetable: the one it says it's late or early for (within 90 s), else
     * the nearest within 4 minutes (TransLink's delay is sometimes off by far more; the 106 runs 7 to 8 minutes apart at
     * peak, so a wider match could take the next bus's place). The arrival moves with it. A live
     * bus the timetable doesn't have joins as it is, with the nearest bus's ride time.
     */
    private static void match(List<Bus> planned, Live l, String route) {
        Bus best = l.hasDelay ? nearest(planned, l.time - l.delay * 1000L, 90_000L) : null;
        if (best == null) best = nearest(planned, l.time, 4 * 60_000L);
        if (best != null) {
            best.arrive += l.time - best.depart;
            best.depart = l.time;
            best.live = true;
        } else {
            // Past the timetable's end there's nothing to take a ride time from: the arrival is unknown (0).
            Bus near = nearest(planned, l.time, Long.MAX_VALUE);
            Bus b = new Bus();
            b.route = route;
            b.depart = l.time;
            b.arrive = near == null ? 0 : l.time + near.arrive - near.depart;
            b.live = true;
            planned.add(b);
        }
    }

    /** The timetable bus (not yet live) departing nearest `time`, within `most` ms; null when none. */
    private static Bus nearest(List<Bus> planned, long time, long most) {
        Bus best = null;
        long bestOff = most;
        for (Bus b : planned) {
            long off = Math.abs(time - b.depart);
            if (!b.live && off <= bestOff) {
                best = b;
                bestOff = off;
            }
        }
        return best;
    }

    /** Midnight `days` days from `now`'s day, on the TV's clock. */
    static long midnight(long now, int days) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    /** `minute` minutes after the service day `day` began (noon minus 12 hours, as GTFS counts: right across DST). */
    private static long at(long day, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.add(Calendar.MINUTE, minute - 12 * 60);
        return c.getTimeInMillis();
    }

    static int date(long day) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(day);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Whole minutes from `now` to `at`, rounded down (0 = under a minute: "Leave now"). */
    static int minutesTo(long at, long now) {
        return (int) Math.max(0, (at - now) / 60_000L);
    }

    /** Where `leave` sits on the timeline, 0 (now) to 1 (WINDOW_MIN ahead). */
    static float onTimeline(long leave, long now) {
        return Math.max(0, Math.min(1, (leave - now) / (WINDOW_MIN * 60_000f)));
    }
}
