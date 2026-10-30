import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Writes app/assets/buses.txt, the board's built-in timetable, from TransLink's static GTFS
 * (https://gtfs-static.translink.ca/gtfs/google_transit.zip). Run on the computer when TransLink publishes a new one:
 * `java tools/BusTimetable.java google_transit.zip app/assets/buses.txt`.
 * Only the owner's bus and stop pairs, and one day of times per day type (weekday, Saturday, Sunday and holidays).
 */
public class BusTimetable {
    // Destination, route on the sign, route id, stop on the sign, stop id in the feed, walk in minutes, headsign.
    static final String[][] PAIRS = {
        {"Edmonds Station", "106", "6645", "52452", "2476", "4", "To Edmonds Station"},
        {"Edmonds Station", "119", "32799", "52406", "2430", "8", "To Edmonds Station"},
        {"Edmonds Station", "133", "33292", "52406", "2430", "8", "Edmonds Station"},
        {"Metrotown", "119", "32799", "52454", "2478", "7", "To Metrotown Station"},
    };
    static final String[] DAYS = {"1", "2", "3"};   // weekday, Saturday, Sunday (and holidays) service ids

    public static void main(String[] args) throws IOException {
        Map<String, List<String[]>> files = new HashMap<>();
        Set<String> want = new HashSet<>(Arrays.asList("calendar.txt", "calendar_dates.txt", "feed_info.txt", "trips.txt", "stops.txt"));
        Map<String, String[]> trips = new HashMap<>();   // trip id → its pair index and day
        List<String[]> times = new ArrayList<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            for (String name : want) files.put(name, read(zip, name));
            Map<String, String> stopName = new HashMap<>();
            for (String[] s : files.get("stops.txt")) stopName.put(s[0], s[2]);
            for (String[] t : files.get("trips.txt")) {
                for (int p = 0; p < PAIRS.length; p++) {
                    if (t[0].equals(PAIRS[p][2]) && Arrays.asList(DAYS).contains(t[1]) && t[3].endsWith(PAIRS[p][6])) {
                        trips.put(t[2], new String[] {String.valueOf(p), t[1]});
                    }
                }
            }
            // One pass over stop_times (90 MB) keeps our trips' stops; the file isn't in stop order, so sort each trip.
            Map<String, List<String[]>> stops = new HashMap<>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry("stop_times.txt")), StandardCharsets.UTF_8))) {
                in.readLine();
                for (String line; (line = in.readLine()) != null; ) {
                    String[] f = line.split(",", -1);
                    if (trips.containsKey(f[0])) stops.computeIfAbsent(f[0], k -> new ArrayList<>()).add(f);
                }
            }
            // Our stop's departure, then the trip's first stop at the station after it.
            for (Map.Entry<String, List<String[]>> e : stops.entrySet()) {
                String[] trip = trips.get(e.getKey());
                String[] pair = PAIRS[Integer.parseInt(trip[0])];
                String station = pair[0].equals("Metrotown") ? "Metrotown Station" : "Edmonds Station";
                e.getValue().sort(Comparator.comparingInt(f -> Integer.parseInt(f[4])));
                Integer dep = null;
                for (String[] f : e.getValue()) {
                    if (f[3].equals(pair[4])) {
                        dep = minute(f[2]);
                    } else if (dep != null && stopName.getOrDefault(f[3], "").startsWith(station)) {
                        times.add(new String[] {trip[0], trip[1], String.valueOf(dep), String.valueOf(minute(f[1]))});
                        break;
                    }
                }
            }
        }
        String[] calendar = files.get("calendar.txt").get(0);
        String[] feed = files.get("feed_info.txt").get(0);
        StringBuilder out = new StringBuilder();
        out.append("# 808 TV's bus timetable, written by tools/BusTimetable.java from TransLink's GTFS ").append(feed[5]).append('\n');
        out.append("# valid <first day> <last day>; day <date> <w|s|u> for days that run another day's buses;\n");
        out.append("# pair <destination>|<route>|<stop>|<walk min>|<feed's route id>|<feed's stop id>, then w, s and u lines of <departure>-<arrival> in minutes after midnight.\n");
        out.append("valid ").append(feed[3]).append(' ').append(feed[4]).append('\n');
        TreeMap<String, String> days = new TreeMap<>();
        for (String[] d : files.get("calendar_dates.txt")) {
            int i = Arrays.asList(DAYS).indexOf(d[0]);
            if (i >= 0 && d[2].equals("1")) days.put(d[1], "wsu".substring(i, i + 1));
        }
        for (Map.Entry<String, String> d : days.entrySet()) out.append("day ").append(d.getKey()).append(' ').append(d.getValue()).append('\n');
        for (int p = 0; p < PAIRS.length; p++) {
            out.append("pair ").append(PAIRS[p][0]).append('|').append(PAIRS[p][1]).append('|').append(PAIRS[p][3]).append('|').append(PAIRS[p][5])
                    .append('|').append(PAIRS[p][2]).append('|').append(PAIRS[p][4]).append('\n');
            for (int d = 0; d < DAYS.length; d++) {
                TreeSet<String> row = new TreeSet<>(Comparator.comparingInt(s -> Integer.parseInt(s.split("-")[0])));
                for (String[] t : times) {
                    if (t[0].equals(String.valueOf(p)) && t[1].equals(DAYS[d])) row.add(t[2] + "-" + t[3]);
                }
                out.append("wsu".charAt(d)).append(' ').append(String.join(" ", row)).append('\n');
            }
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(args[1]), StandardCharsets.UTF_8)) {
            w.write(out.toString());
        }
        System.out.println("Wrote " + args[1] + " (" + times.size() + " trips, valid to " + feed[4] + ", calendar starts " + calendar[8] + ")");
    }

    static List<String[]> read(ZipFile zip, String name) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(name)), StandardCharsets.UTF_8))) {
            in.readLine();
            for (String line; (line = in.readLine()) != null; ) rows.add(line.split(",", -1));
        }
        return rows;
    }

    /** " 5:05:00" or "25:10:00" → minutes after the service day's midnight (past 1440 after midnight). */
    static int minute(String t) {
        String[] f = t.trim().split(":");
        return Integer.parseInt(f[0]) * 60 + Integer.parseInt(f[1]);
    }
}
