package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional crypto prices (Binance, every 5 min) and weather/sunrise/sunset (Open-Meteo, every 30 min), cached
 * so the screen opens with numbers. Only fetched while the home screen or the screensaver is visible (the screen asks).
 * Neither source needs a key. Location and currency come from config.properties.
 */
final class HomeData {
    static final String[] COINS = {"BTC", "ETH", "SOL", "USDT"};
    static final String[] COIN_NAMES = {"Bitcoin", "Ethereum", "Solana", "Dollar"};
    private static final String CURRENCY = Place.currency();
    /** Binance has no pairs in some currencies (INR...) and then refuses the whole request: prices go in dollars instead. */
    private static volatile boolean inDollars;

    /** Binance quotes dollars as USDT. */
    private static String quote() {
        return CURRENCY.equals("USD") || inDollars ? "USDT" : CURRENCY;
    }
    // Both forecasts in one answer, the next 48 hours and today plus 5 days: Home Settings → Forecast switches without
    // asking again, and offline too.
    private static final String WEATHER_QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,weather_code"
            + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&timezone=auto&forecast_days=6&forecast_hours=48";

    private static String weatherPlace() {
        return Prefs.latitude() + "," + Prefs.longitude();
    }

    private static String weatherUrl() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude=" + Prefs.longitude() + WEATHER_QUERY;
    }
    private static final long PRICES_EVERY = 5 * 60_000L, WEATHER_EVERY = 30 * 60_000L;

    /**
     * Environment Canada (api.weather.gc.ca, no key): the air quality (AQHI) of the nearest station and the weather
     * alerts at your location, every hour while the board shows. The owner's sources: Burnaby is in Metro Vancouver NW.
     */
    private static final String AQHI_STATION = "JBRIK";
    private static final long CANADA_EVERY = 60 * 60_000L;

    private static String aqhiUrl() {
        return "https://api.weather.gc.ca/collections/aqhi-observations-realtime/items?f=json&skipGeometry=true&limit=1"
                + "&sortby=-observation_datetime&location_id=" + AQHI_STATION;
    }

    /** The alerts whose area holds your location: a box of about 100 m around it, without the areas' outlines. */
    private static String alertsUrl() {
        double lat = Double.parseDouble(Prefs.latitude()), lon = Double.parseDouble(Prefs.longitude());
        return String.format(Locale.US, "https://api.weather.gc.ca/collections/weather-alerts/items?f=json&skipGeometry=true"
                + "&properties=alert_type,alert_name_en,risk_colour_en,status_en,event_end_datetime,expiration_datetime"
                + "&bbox=%.4f,%.4f,%.4f,%.4f", lon - 0.001, lat - 0.001, lon + 0.001, lat + 0.001);
    }

    /**
     * TransLink's live feed (GTFS-Realtime, the owner's key): every bus under way in the region, about 1 MB, every
     * 5 minutes while the board shows. The built-in timetable (assets/buses.txt) shows before it comes and fills gaps.
     */
    private static final long BUSES_EVERY = 5 * 60_000L, RESULTS_EVERY = 6 * 3600_000L;
    private static final String ESPN = "https://site.api.espn.com/apis/site/v2/sports/";
    /** ESPN's league, team id, the board's abbreviation and the league's name. */
    private static final String[][] TEAMS = {{"soccer/eng.1", "367", "TOT", "Premier League"}, {"soccer/usa.1", "9727", "VAN", "MLS"}};
    private static final java.util.Set<String> BUS_STOPS = new java.util.HashSet<>();

    /** The board's buses: only in a build with the owner's TransLink key (their stops). */
    static boolean busesOn() {
        return !Config.TRANSLINK_KEY.isEmpty() || Config.DEMO;
    }

    /** An active warning, watch or advisory (statements are left out: the air line already covers smoke). */
    static final class Alert {
        String name;        // "Rainfall warning", as Environment Canada names it
        int level;          // Rules.ADVISORY, WATCH or WARNING
        int colour;         // Environment Canada's risk colour
        long ends, expires; // epoch ms; 0 = not given
    }

    interface Listener { void onData(); }

    /** BTC, ETH and SOL; in Brazilian reais also the dollar (USDT/BRL), which Binance doesn't list for most currencies. */
    static int coinCount() {
        return Prefs.quotes() ? active().length : 0;
    }

    private static volatile int[] activeCoins;
    private static volatile int activeVersion = -1;
    private static volatile boolean activeInDollars;

    /** Indexes in COINS of the coins the user has on (and that Binance lists in this currency). */
    static int[] active() {
        int[] a = activeCoins;
        boolean dollars = inDollars;
        if (a != null && activeVersion == Prefs.version && activeInDollars == dollars) return a;
        int n = 0;
        int[] tmp = new int[COINS.length];
        // The dollar only in reais, not in dollars: USDTUSDT isn't a pair, and Binance would refuse the whole request.
        for (int i = 0; i < COINS.length; i++) if (Prefs.coin(i) && (i < 3 || CURRENCY.equals("BRL") && !dollars)) tmp[n++] = i;
        a = java.util.Arrays.copyOf(tmp, n);
        activeCoins = a;
        activeVersion = Prefs.version;
        activeInDollars = dollars;
        return a;
    }

    /** Settings changed (city, coins): fetch again now. */
    void settingsChanged() {
        pricesAt = 0;
        weatherAt = 0;
        pricesFailedAt = 0;
        weatherFailedAt = 0;
    }

    static String coinName(int i) {
        return i == 3 ? Ui.tr("Dollar") : COIN_NAMES[i];
    }

    /** Binance.com refuses the US (HTTP 451); Binance.US answers the same call in dollars. */
    private static final String[] PRICE_HOSTS = {"https://api.binance.com", "https://api.binance.us"};

    private static String pricesPath() {
        StringBuilder symbols = new StringBuilder();
        int[] coins = active();
        for (int i = 0; i < coins.length; i++) symbols.append(i == 0 ? "" : ",").append("%22").append(COINS[coins[i]]).append(quote()).append("%22");
        return "/api/v3/ticker/24hr?type=MINI&symbols=%5B" + symbols + "%5D";
    }

    private static String getPrices() throws IOException {
        IOException failed = null;
        for (String host : PRICE_HOSTS) {
            try {
                return get(host + pricesPath());
            } catch (IOException e) {
                // Binance.US has only dollar pairs: its error counts only where Binance.com refuses the US (HTTP 451).
                // After a timeout or a 5xx there, its HTTP 400 would put the prices in dollars for good.
                if (failed == null || "HTTP 451".equals(failed.getMessage())) failed = e;
                // A pair Binance doesn't list (HTTP 400) isn't a refusal: the caller falls back to dollars instead.
                if ("HTTP 400".equals(e.getMessage())) break;
            }
        }
        throw failed;
    }

    static final class Weather {
        double temp = Double.NaN, max = Double.NaN, min = Double.NaN;
        int code = -1;
        boolean isDay = true;
        long sunrise, sunset;   // today's, in epoch ms
        // Next hours (starting at the next full hour): time, temperature, code and day/night.
        final List<Long> hours = new ArrayList<>();
        final List<Double> hourTemp = new ArrayList<>();
        final List<Integer> hourCode = new ArrayList<>();
        final List<Boolean> hourDay = new ArrayList<>();
        final List<Integer> hourRain = new ArrayList<>();
        // Today and the next 5 days: noon of each day on the TV's clock, high, low and code.
        final List<Long> days = new ArrayList<>();
        final List<Double> dayMax = new ArrayList<>();
        final List<Double> dayMin = new ArrayList<>();
        final List<Integer> dayCode = new ArrayList<>();
    }

    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData(c.getApplicationContext());
        return instance;
    }

    final double[] prices = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    /** 24 h change (%), in the same order. */
    final double[] changes = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    volatile long pricesAt, weatherAt;
    volatile Weather weather = new Weather();

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile boolean fetchingPrices, fetchingWeather, fetchingCanada;
    private volatile long pricesFailedAt, weatherFailedAt, canadaFailedAt;
    /** The AQHI now (NaN = unknown) and the most serious alert (null = none). */
    volatile double aqhi = Double.NaN;
    volatile Alert alert;
    volatile long canadaAt;
    /** The timetable (null until read, or without buses) and the last live feed (null: none yet). */
    volatile Transit.Table buses;
    volatile Transit.Feed busFeed;
    private volatile boolean fetchingBuses;
    private volatile long busesAt, busesFailedAt;
    /** The owner's teams (Spurs, Whitecaps) and the F1 weekend: null until the first fetch. */
    volatile Sports.Team[] teams;
    volatile Sports.Weekend f1;
    volatile long sportsAt;
    private volatile boolean fetchingSports;
    private volatile long sportsFailedAt, sportsDue, resultsAt;
    /** Sleeper teams (null until the first fetch; none when it isn't set up) and the FPL strip (null: none). */
    volatile Fantasy.Team[] fantasy;
    volatile Fantasy.Fpl fpl;
    volatile long fantasyAt;
    private volatile boolean fetchingFantasy;
    private volatile long fantasyFailedAt;
    /** The city of the forecast in memory: another process (ScreensaverAlone) can see the city change under it. */
    private volatile String weatherFor = "";

    private HomeData(Context c) {
        cache = c.getSharedPreferences("homedata", Context.MODE_PRIVATE);
        inDollars = CURRENCY.equals(cache.getString("dollarsFor", ""));
        try {
            if (Prefs.quotes()) {
                String p = cache.getString("prices", null);
                if (p != null) parsePrices(p);
                pricesAt = cache.getLong("pricesAt", 0);
            }
            String w = cache.getString("weather", null);
            // The cached forecast is for the city it was fetched for; another city means fetch again.
            if (w != null && weatherPlace().equals(cache.getString("weatherPlace", ""))) {
                weather = parseWeather(w);
                // Fetched with another query (an older version): shown, and fetched again now.
                weatherAt = WEATHER_QUERY.equals(cache.getString("weatherQuery", "")) ? cache.getLong("weatherAt", 0) : 0;
                weatherFor = weatherPlace();
            }
        } catch (Exception ignored) {}
        if (busesOn()) {
            io.execute(() -> {
                try (InputStream in = c.getAssets().open("buses.txt")) {
                    Transit.Table t = Transit.parse(read(in));
                    for (Transit.Pair p : t.pairs) BUS_STOPS.add(p.stopId);
                    buses = t;
                    notifyListeners();
                } catch (Exception e) {
                    AppLog.w("buses: no timetable: " + e.getMessage());
                }
            });
        }
        // As for the picks: a fetch made before the Wi-Fi is up (the TV just started) is tried again when it comes.
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            if (cm != null) cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    if (pricesFailedAt == 0 && weatherFailedAt == 0 && canadaFailedAt == 0 && busesFailedAt == 0) return;
                    pricesFailedAt = 0;
                    weatherFailedAt = 0;
                    canadaFailedAt = 0;
                    busesFailedAt = 0;
                    // Only while a screen shows (it listens): nothing is fetched behind another app.
                    main.postDelayed(() -> { if (!listeners.isEmpty()) refreshIfStale(); }, 2000);
                }
            });
        } catch (RuntimeException e) {
            AppLog.w("weather: no network callback: " + e.getMessage());
        }
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Fetches whatever is stale. The screen calls this when it appears and every minute while it's visible. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        // A negative age (the TV's clock went back) counts as stale: otherwise the data would freeze.
        if (!fetchingPrices && coinCount() > 0 && (now - pricesAt > PRICES_EVERY || now < pricesAt) && !failedRecently(pricesFailedAt, now)) {
            fetchingPrices = true;
            io.execute(() -> {
                try {
                    String body;
                    try {
                        body = getPrices();
                    } catch (IOException e) {
                        if (inDollars || CURRENCY.equals("USD") || !"HTTP 400".equals(e.getMessage())) throw e;
                        inDollars = true;
                        cache.edit().putString("dollarsFor", CURRENCY).apply();
                        AppLog.w("prices: Binance has no " + CURRENCY + " pairs, using dollars");
                        body = getPrices();
                    }
                    if (!inDollars && !CURRENCY.equals("USD") && halted(body, System.currentTimeMillis())) {
                        // Not saved, unlike a currency Binance doesn't list: the market may open again (next start).
                        inDollars = true;
                        AppLog.w("prices: Binance has halted its " + CURRENCY + " pairs, using dollars");
                        body = getPrices();
                    }
                    parsePrices(body);
                    pricesAt = System.currentTimeMillis();
                    cache.edit().putString("prices", body).putLong("pricesAt", pricesAt).apply();
                    notifyListeners();
                } catch (Exception e) {
                    pricesFailedAt = System.currentTimeMillis();
                    AppLog.w("prices: " + e.getMessage());
                } finally {
                    fetchingPrices = false;
                }
            });
        }
        boolean moved = !weatherPlace().equals(weatherFor);
        if (!fetchingWeather && (moved || now - weatherAt > WEATHER_EVERY || now < weatherAt) && !failedRecently(weatherFailedAt, now)) {
            fetchingWeather = true;
            io.execute(() -> {
                try {
                    String place = weatherPlace();
                    String body = get(weatherUrl());
                    weather = parseWeather(body);
                    weatherAt = System.currentTimeMillis();
                    weatherFor = place;
                    cache.edit().putString("weather", body).putLong("weatherAt", weatherAt).putString("weatherPlace", place)
                            .putString("weatherQuery", WEATHER_QUERY).apply();
                    notifyListeners();
                } catch (Exception e) {
                    weatherFailedAt = System.currentTimeMillis();
                    AppLog.w("weather: " + e.getMessage());
                } finally {
                    fetchingWeather = false;
                }
            });
        }
        if (Prefs.weather()) refreshCanada(now);
    }

    /** The bus feed when it's stale. Only the home screen asks (the screensaver shows no buses). */
    void refreshBuses() {
        long now = System.currentTimeMillis();
        if (!busesOn() || Config.TRANSLINK_KEY.isEmpty() || fetchingBuses || buses == null || (now - busesAt <= BUSES_EVERY && now >= busesAt) || failedRecently(busesFailedAt, now)) return;
        fetchingBuses = true;
        io.execute(() -> {
            try {
                // The key goes only in the address, and an error never names the address.
                busFeed = Transit.decode(getBytes("https://gtfsapi.translink.ca/v3/gtfsrealtime?apikey=" + Config.TRANSLINK_KEY, false), BUS_STOPS);
                busesAt = System.currentTimeMillis();
                notifyListeners();
            } catch (Exception e) {
                busesFailedAt = System.currentTimeMillis();
                AppLog.w("buses: " + e.getClass().getSimpleName() + (e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("HTTP") ? " " + e.getMessage() : ""));
            } finally {
                fetchingBuses = false;
            }
        });
    }

    /**
     * The owner's teams and the F1 weekend from ESPN's public feed: every minute while something is on or about to
     * start, else every half hour; the results (the schedule, up to 470 KB) every 6 hours and when a game ends. Only
     * the home screen asks.
     */
    void refreshSports() {
        long now = System.currentTimeMillis();
        if (fetchingSports || (now < sportsDue && now >= sportsAt) || failedRecently(sportsFailedAt, now)) return;
        fetchingSports = true;
        io.execute(() -> {
            try {
                Sports.Team[] old = teams, t = new Sports.Team[TEAMS.length];
                Sports.Game[] fin = new Sports.Game[TEAMS.length];
                boolean ended = false;
                for (int i = 0; i < TEAMS.length; i++) {
                    t[i] = new Sports.Team(TEAMS[i][2], TEAMS[i][3]);
                    JSONObject team = new JSONObject(get(ESPN + TEAMS[i][0] + "/teams/" + TEAMS[i][1])).getJSONObject("team");
                    t[i].name = team.optString("shortDisplayName");
                    t[i].standing = team.optString("standingSummary");
                    JSONArray next = team.optJSONArray("nextEvent");
                    if (next != null && next.length() > 0) t[i].next = Sports.usable(SportsFeed.game(next.getJSONObject(0), TEAMS[i][1]));
                    // The team page names the next fixture as soon as a game starts: the game in play is on the scoreboard.
                    Sports.Game was = old == null ? null : old[i].next;
                    if (Sports.watching(was, now)) {
                        Sports.Game g = Sports.usable(scoreboardGame(TEAMS[i], was == null ? now : was.at));
                        if (g != null && g.state == Sports.IN) t[i].next = g;
                        else if (g != null && g.state == Sports.POST) fin[i] = g;
                    }
                    ended |= was != null && was.state == Sports.IN && (t[i].next == null || t[i].next.state != Sports.IN);
                }
                boolean results = old == null || ended || now - resultsAt > RESULTS_EVERY || now < resultsAt;
                for (int i = 0; i < TEAMS.length; i++) {
                    if (!results) {
                        t[i].results.addAll(old[i].results);
                        Sports.addResult(t[i].results, fin[i]);
                        continue;
                    }
                    JSONArray events = new JSONObject(get(ESPN + TEAMS[i][0] + "/teams/" + TEAMS[i][1] + "/schedule")).getJSONArray("events");
                    for (int j = 0; j < events.length(); j++) {
                        Sports.Game g = SportsFeed.game(events.getJSONObject(j), TEAMS[i][1]);
                        if (g.state == Sports.POST && g.us >= 0 && g.them >= 0) t[i].results.add(g);
                    }
                    t[i].results.sort((a, b) -> Long.compare(b.at, a.at));
                    while (t[i].results.size() > 5) t[i].results.remove(5);
                    Sports.addResult(t[i].results, fin[i]);
                }
                if (results) resultsAt = now;
                Sports.Weekend w = SportsFeed.weekend(new JSONObject(get(ESPN + "racing/f1/scoreboard")));
                boolean live = false;
                long soonest = 0;
                for (Sports.Team x : t) {
                    if (x.next == null) continue;
                    live |= x.next.state == Sports.IN;
                    if (x.next.state == Sports.PRE && (soonest == 0 || x.next.at < soonest)) soonest = x.next.at;
                }
                for (Sports.Session s : w.sessions) {
                    live |= s.state == Sports.IN;
                    if (s.state == Sports.PRE && (soonest == 0 || s.at < soonest)) soonest = s.at;
                }
                f1 = w;
                teams = t;
                sportsAt = System.currentTimeMillis();
                sportsDue = sportsAt + Sports.every(live, soonest, sportsAt);
                notifyListeners();
            } catch (Exception e) {
                sportsFailedAt = System.currentTimeMillis();
                AppLog.w("sports: " + e.getClass().getSimpleName() + (e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("HTTP") ? " " + e.getMessage() : ""));
            } finally {
                fetchingSports = false;
            }
        });
    }

    /** The team's game on its league's scoreboard for the day (US Eastern) of `at`, or null. */
    private static Sports.Game scoreboardGame(String[] team, long at) throws Exception {
        JSONArray events = new JSONObject(get(ESPN + team[0] + "/scoreboard?dates=" + Sports.etDay(at))).optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            JSONArray sides = e.getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");
            for (int k = 0; k < sides.length(); k++) if (team[1].equals(sides.getJSONObject(k).optString("id"))) return SportsFeed.game(e, team[1]);
        }
        return null;
    }

    /** The Fantasy row: only in a build with the owner's Sleeper username or FPL team. */
    static boolean fantasyOn() {
        return Config.DEMO || !Config.SLEEPER_USER.isEmpty() || Config.FPL_TEAM.matches("\\d+");
    }

    /**
     * Sleeper's NFL leagues and the FPL strip, every 5 minutes while the home screen shows (it asks; the screensaver
     * doesn't). Slow parts are kept: Sleeper's leagues and rosters for an hour, FPL's gameweeks (1.8 MB) for a day.
     */
    void refreshFantasy() {
        long now = System.currentTimeMillis();
        if (!fantasyOn() || fetchingFantasy || (now - fantasyAt <= Fantasy.EVERY && now >= fantasyAt) || failedRecently(fantasyFailedAt, now)) return;
        fetchingFantasy = true;
        io.execute(() -> {
            try {
                if (Config.DEMO) {
                    fantasy = demoTeams();
                    fpl = demoFpl(now);
                    fantasyAt = System.currentTimeMillis();
                    notifyListeners();
                    return;
                }
                Fantasy.Team[] sl = Config.SLEEPER_USER.isEmpty() ? new Fantasy.Team[0] : sleeper(now);
                Fantasy.Fpl f = Config.FPL_TEAM.matches("\\d+") ? fpl(now) : null;
                fantasy = sl;
                fpl = f;
                fantasyAt = System.currentTimeMillis();
                notifyListeners();
            } catch (Exception e) {
                fantasyFailedAt = System.currentTimeMillis();
                AppLog.w("fantasy: " + e.getClass().getSimpleName() + (e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("HTTP") ? " " + e.getMessage() : ""));
            } finally {
                fetchingFantasy = false;
            }
        });
    }

    /** Made-up teams for screenshots (demo=true in config.properties). */
    private static Fantasy.Team[] demoTeams() {
        Fantasy.Team a = new Fantasy.Team(), b = new Fantasy.Team();
        a.league = "Sunday League";
        a.name = "Gridiron Gang";
        a.opp = "Fourth and Long";
        a.wins = 6;
        a.losses = 2;
        a.place = 2;
        a.size = 10;
        a.week = 9;
        a.mine = 104.6;
        a.theirs = 88.2;
        a.hasOpp = a.hasLast = true;
        a.lastMine = 121.4;
        a.lastTheirs = 97.0;
        b.league = "Office Pool";
        b.name = "Blitz Krieg";
        b.opp = "Hail Mary";
        b.wins = 4;
        b.losses = 4;
        b.place = 5;
        b.size = 8;
        b.week = 9;
        b.mine = 67.3;
        b.theirs = 91.8;
        b.hasOpp = b.hasLast = true;
        b.lastMine = 88.5;
        b.lastTheirs = 102.1;
        return new Fantasy.Team[] {a, b};
    }

    private static Fantasy.Fpl demoFpl(long now) {
        Fantasy.Fpl f = new Fantasy.Fpl();
        f.name = "Offside Trap";
        f.league = "Office League";
        f.gw = 9;
        f.points = 61;
        f.rank = 3;
        f.lastRank = 5;
        f.nextGw = 10;
        f.start = now + 30 * 3600_000L;
        f.deadline = now + 26 * 3600_000L;
        f.end = now + 100 * 3600_000L;
        return f;
    }

    private static final String SLEEPER = "https://api.sleeper.app/v1/";
    /** One Sleeper league as read once an hour: our roster and everyone's name and record. */
    private static final class SleeperLeague {
        String id, name = "";
        int me = -1, week;
        int[] roster, wins, losses, ties;
        double[] points;
        final java.util.Map<Integer, String> names = new java.util.HashMap<>();
    }
    private List<SleeperLeague> sleeperLeagues = new ArrayList<>();
    private long sleeperStaticAt;
    private final java.util.Map<String, double[]> lastWeek = new java.util.HashMap<>();

    private Fantasy.Team[] sleeper(long now) throws Exception {
        JSONObject state = new JSONObject(get(SLEEPER + "state/nfl"));
        int week = state.optInt("week", 1);
        String season = state.optString("league_season", state.optString("season"));
        if (sleeperLeagues.isEmpty() || now - sleeperStaticAt > 3600_000L || now < sleeperStaticAt || sleeperWeek != week) {
            String user = new JSONObject(get(SLEEPER + "user/" + URLEncoder.encode(Config.SLEEPER_USER, "UTF-8"))).getString("user_id");
            JSONArray leagues = new JSONArray(get(SLEEPER + "user/" + user + "/leagues/nfl/" + season));
            List<SleeperLeague> out = new ArrayList<>();
            lastWeek.clear();
            for (int i = 0; i < leagues.length(); i++) {
                JSONObject l = leagues.getJSONObject(i);
                SleeperLeague s = new SleeperLeague();
                s.id = l.getString("league_id");
                s.name = l.optString("name");
                JSONArray rosters = new JSONArray(get(SLEEPER + "league/" + s.id + "/rosters"));
                int n = rosters.length();
                s.roster = new int[n];
                s.wins = new int[n];
                s.losses = new int[n];
                s.ties = new int[n];
                s.points = new double[n];
                java.util.Map<String, Integer> owner = new java.util.HashMap<>();
                for (int j = 0; j < n; j++) {
                    JSONObject r = rosters.getJSONObject(j), st = r.getJSONObject("settings");
                    s.roster[j] = r.getInt("roster_id");
                    s.wins[j] = st.optInt("wins");
                    s.losses[j] = st.optInt("losses");
                    s.ties[j] = st.optInt("ties");
                    s.points[j] = st.optInt("fpts") + st.optInt("fpts_decimal") / 100.0;
                    owner.put(r.optString("owner_id"), j);
                    if (user.equals(r.optString("owner_id"))) s.me = j;
                }
                JSONArray users = new JSONArray(get(SLEEPER + "league/" + s.id + "/users"));
                for (int j = 0; j < users.length(); j++) {
                    JSONObject u = users.getJSONObject(j);
                    Integer at = owner.get(u.optString("user_id"));
                    if (at == null) continue;
                    JSONObject meta = u.optJSONObject("metadata");
                    String team = meta == null ? "" : meta.optString("team_name").trim();
                    s.names.put(s.roster[at], team.isEmpty() ? u.optString("display_name") : team);
                }
                if (s.me >= 0) {
                    s.week = week;
                    if (week > 1) lastWeek.put(s.id, matchup(s, week - 1));
                    out.add(s);
                }
            }
            sleeperLeagues = out;
            sleeperStaticAt = now;
            sleeperWeek = week;
        }
        List<Fantasy.Team> teams = new ArrayList<>();
        for (SleeperLeague s : sleeperLeagues) {
            Fantasy.Team t = new Fantasy.Team();
            t.league = s.name;
            t.week = week;
            t.size = s.roster.length;
            t.wins = s.wins[s.me];
            t.ties = s.ties[s.me];
            t.losses = s.losses[s.me];
            t.name = s.names.containsKey(s.roster[s.me]) ? s.names.get(s.roster[s.me]) : "";
            t.place = Fantasy.place(s.me, s.wins, s.ties, s.points);
            double[] now8 = matchup(s, week);
            t.mine = now8[0];
            t.theirs = now8[1];
            t.hasOpp = now8[2] > 0;
            t.opp = t.hasOpp ? s.names.get((int) now8[2]) : "";
            if (t.opp == null) t.opp = "";
            double[] last = lastWeek.get(s.id);
            if (last != null && last[2] > 0) {
                t.lastMine = last[0];
                t.lastTheirs = last[1];
                t.hasLast = true;
            }
            teams.add(t);
        }
        return teams.toArray(new Fantasy.Team[0]);
    }

    private int sleeperWeek;

    /** Our points, the opponent's and the opponent's roster id (0: none) in `week`. */
    private double[] matchup(SleeperLeague s, int week) throws Exception {
        JSONArray rows = new JSONArray(get(SLEEPER + "league/" + s.id + "/matchups/" + week));
        int mineId = s.roster[s.me], group = -1;
        double mine = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.getJSONObject(i);
            if (r.getInt("roster_id") == mineId) {
                mine = r.optDouble("points", 0);
                group = r.optInt("matchup_id", -1);
            }
        }
        double theirs = 0, opp = 0;
        for (int i = 0; i < rows.length() && group > 0; i++) {
            JSONObject r = rows.getJSONObject(i);
            if (r.getInt("roster_id") != mineId && r.optInt("matchup_id", -2) == group) {
                theirs = r.optDouble("points", 0);
                opp = r.getInt("roster_id");
            }
        }
        return new double[] {mine, theirs, opp};
    }

    private static final String FPL = "https://fantasy.premierleague.com/api/";
    private long fplDailyAt;
    private int fplCurrent, fplNext;
    private boolean fplFinished;
    private long fplDeadlineCurrent, fplDeadlineNext;
    private long fplStart, fplEnd;
    private int fplFixturesFor;

    private Fantasy.Fpl fpl(long now) throws Exception {
        if (fplDailyAt == 0 || now - fplDailyAt > 24 * 3600_000L || now < fplDailyAt) {
            // Only the gameweeks out of 1.8 MB: found by their bracket, so the players aren't parsed.
            String all = get(FPL + "bootstrap-static/");
            JSONArray events = new JSONArray(array(all, "\"events\":"));
            fplCurrent = fplNext = 0;
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.getJSONObject(i);
                if (e.optBoolean("is_current")) {
                    fplCurrent = e.getInt("id");
                    fplFinished = e.optBoolean("finished");
                    fplDeadlineCurrent = Sports.time(e.optString("deadline_time"));
                }
                if (e.optBoolean("is_next")) {
                    fplNext = e.getInt("id");
                    fplDeadlineNext = Sports.time(e.optString("deadline_time"));
                }
            }
            fplDailyAt = now;
            fplFixturesFor = 0;
        }
        JSONObject entry = new JSONObject(get(FPL + "entry/" + Config.FPL_TEAM + "/"));
        Fantasy.Fpl f = new Fantasy.Fpl();
        f.name = entry.optString("name");
        f.gw = entry.optInt("current_event", fplCurrent);
        f.points = entry.optInt("summary_event_points");
        f.live = fplCurrent > 0 && !fplFinished;
        f.nextGw = f.live ? f.gw : fplNext;
        f.deadline = f.live ? 0 : fplDeadlineNext;
        JSONObject leagues = entry.optJSONObject("leagues");
        JSONArray classic = leagues == null ? null : leagues.optJSONArray("classic");
        for (int i = 0; classic != null && i < classic.length(); i++) {
            JSONObject l = classic.getJSONObject(i);
            if (String.valueOf(l.optInt("id")).equals(Config.FPL_LEAGUE)) {
                f.league = l.optString("name");
                f.rank = l.optInt("entry_rank");
                f.lastRank = l.optInt("entry_last_rank");
            }
        }
        if (f.nextGw > 0 && fplFixturesFor != f.nextGw) {
            JSONArray fixtures = new JSONArray(get(FPL + "fixtures/?event=" + f.nextGw));
            long first = 0, last = 0;
            for (int i = 0; i < fixtures.length(); i++) {
                long at = Sports.time(fixtures.getJSONObject(i).optString("kickoff_time"));
                if (at > 0 && (first == 0 || at < first)) first = at;
                if (at > last) last = at;
            }
            fplStart = first;
            fplEnd = last;
            fplFixturesFor = f.nextGw;
        }
        f.start = f.nextGw > 0 ? fplStart : 0;
        f.end = f.nextGw > 0 ? fplEnd : 0;
        return f;
    }

    /** The JSON array that follows `key` in `json`, as text: found by its brackets (strings can hold them). */
    static String array(String json, String key) throws IOException {
        int at = json.indexOf(key);
        int open = at < 0 ? -1 : json.indexOf('[', at);
        if (open < 0) throw new IOException("no " + key);
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (ch == '\\') i++;
                else if (ch == '"') inString = false;
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '[') {
                depth++;
            } else if (ch == ']' && --depth == 0) {
                return json.substring(open, i + 1);
            }
        }
        throw new IOException("cut " + key);
    }

    /** Environment Canada's AQHI and alerts: only for a place in Canada (its alerts' box finds nothing elsewhere). */
    private void refreshCanada(long now) {
        if (fetchingCanada || (now - canadaAt <= CANADA_EVERY && now >= canadaAt) || failedRecently(canadaFailedAt, now)) return;
        fetchingCanada = true;
        io.execute(() -> {
            try {
                aqhi = parseAqhi(get(aqhiUrl()));
                alert = parseAlerts(get(alertsUrl()), System.currentTimeMillis());
                canadaAt = System.currentTimeMillis();
                notifyListeners();
            } catch (Exception e) {
                canadaFailedAt = System.currentTimeMillis();
                AppLog.w("environment canada: " + e.getMessage());
            } finally {
                fetchingCanada = false;
            }
        });
    }

    private static double parseAqhi(String body) throws Exception {
        JSONArray features = new JSONObject(body).getJSONArray("features");
        if (features.length() == 0) return Double.NaN;
        return features.getJSONObject(0).getJSONObject("properties").optDouble("aqhi", Double.NaN);
    }

    /** The most serious active alert (Rules: which show, and in what order). */
    static Alert parseAlerts(String body, long now) throws Exception {
        JSONArray features = new JSONObject(body).getJSONArray("features");
        Alert best = null;
        int bestRank = -1;
        for (int i = 0; i < features.length(); i++) {
            JSONObject p = features.getJSONObject(i).getJSONObject("properties");
            int level = Rules.alertLevel(p.optString("alert_type", ""));
            long ends = isoTime(p.optString("event_end_datetime", "")), expires = isoTime(p.optString("expiration_datetime", ""));
            String name = p.optString("alert_name_en", ""), colour = p.optString("risk_colour_en", "");
            if (level < 0 || name.isEmpty() || !Rules.alertActive(p.optString("status_en", ""), ends, expires, now)) continue;
            int rank = Rules.alertRank(level, colour);
            if (rank <= bestRank) continue;
            best = new Alert();
            best.name = Rules.sentence(name);
            best.level = level;
            best.colour = Rules.alertColour(colour);
            best.ends = ends;
            best.expires = expires;
            bestRank = rank;
        }
        return best;
    }

    /** "2026-10-30T11:41:00.000Z" in epoch ms; 0 when missing. */
    private static long isoTime(String s) {
        if (s.isEmpty() || s.equals("null")) return 0;
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** "Air 2 · low risk", "Air 10+ · very high risk". */
    static String aqhiLine(double aqhi) {
        String n = Rules.aqhiNumber(aqhi);
        switch (Rules.aqhiRisk(aqhi)) {
            case 0: return Ui.trf("Air {n} · low risk", "n", n);
            case 1: return Ui.trf("Air {n} · moderate risk", "n", n);
            case 2: return Ui.trf("Air {n} · high risk", "n", n);
            default: return Ui.trf("Air {n} · very high risk", "n", n);
        }
    }

    private static boolean failedRecently(long at, long now) {
        return at != 0 && now >= at && now - at < 2 * 60_000L;
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onData(); });
    }

    /**
     * A pair with no trade for a day: Binance halted it and keeps answering with the frozen last price (its AUD pairs
     * since 2026-10-06, BTC at AUD 37,000 for AUD 115,000).
     */
    static boolean halted(String body, long now) throws Exception {
        JSONArray list = new JSONArray(body);
        for (int i = 0; i < list.length(); i++) {
            long close = list.getJSONObject(i).optLong("closeTime", 0);
            if (close > 0 && now - close > 24 * 60 * 60_000L) return true;
        }
        return false;
    }

    private void parsePrices(String body) throws Exception {
        JSONArray list = new JSONArray(body);
        String quote = quote();
        int found = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String symbol = o.getString("symbol");
            for (int k = 0; k < COINS.length; k++) {
                if (!symbol.equals(COINS[k] + quote)) continue;
                found++;
                double last = Double.parseDouble(o.optString("lastPrice", o.optString("price", "NaN")));
                double open = Double.parseDouble(o.optString("openPrice", "NaN"));
                prices[k] = last;
                changes[k] = open > 0 ? (last - open) / open * 100 : Double.NaN;
            }
        }
        if (found == 0) pricesAt = 0;   // cached in another currency (config changed): fetch again
    }

    private static Weather parseWeather(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        // Open-Meteo writes every time at the place's offset of now, even past a DST change (Berlin's sunrise after the
        // change still reads 07:51 at GMT+2): read them at that offset, as LG and Samsung do, not by the zone's rules.
        TimeZone zone = o.has("utc_offset_seconds") ? new SimpleTimeZone(o.getInt("utc_offset_seconds") * 1000, "GMT")
                : TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID()));
        Weather w = new Weather();
        JSONObject current = o.getJSONObject("current");
        w.temp = current.getDouble("temperature_2m");
        w.code = current.getInt("weather_code");
        w.isDay = current.optInt("is_day", 1) == 1;
        JSONObject daily = o.getJSONObject("daily");
        w.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        w.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        w.sunrise = localTime(daily.getJSONArray("sunrise").getString(0), zone);
        w.sunset = localTime(daily.getJSONArray("sunset").getString(0), zone);
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray codes = hourly.getJSONArray("weather_code");
            JSONArray days = hourly.optJSONArray("is_day"), rain = hourly.optJSONArray("precipitation_probability");
            long now = System.currentTimeMillis();
            for (int i = 0; i < times.length() && w.hours.size() < 7; i++) {
                long at = localTime(times.getString(i), zone);
                if (at <= now) continue;
                w.hours.add(at);
                w.hourTemp.add(temps.getDouble(i));
                w.hourCode.add(codes.getInt(i));
                w.hourDay.add(days == null || days.optInt(i, 1) == 1);
                w.hourRain.add(rain == null ? -1 : rain.optInt(i, -1));
            }
        }
        JSONArray dates = daily.getJSONArray("time"), highs = daily.getJSONArray("temperature_2m_max"),
                lows = daily.getJSONArray("temperature_2m_min"), codes = daily.optJSONArray("weather_code");
        long now = System.currentTimeMillis();
        for (int i = 0; codes != null && i < dates.length() && w.days.size() < 6; i++) {
            String date = dates.getString(i);
            // Past days (in a forecast kept offline) are left out; today stays.
            if (Rules.dayEnded(localTime(date + "T12:00", zone), now)) continue;
            w.days.add(localTime(date + "T12:00", TimeZone.getDefault()));   // its noon on the TV's clock: named right anywhere
            w.dayMax.add(highs.optDouble(i));
            w.dayMin.add(lows.optDouble(i));
            w.dayCode.add(codes.optInt(i, -1));
        }
        return w;
    }

    private static long localTime(String iso, TimeZone zone) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.parse(iso).getTime();
    }

    static List<String[]> findCity(String name) throws Exception {
        String lang = Ui.language.replaceAll("-.*", "");   // Open-Meteo takes the language without the country
        JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language=" + lang
                + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")));
        JSONArray results = o.optJSONArray("results");
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();   // places with the same name, state and country read the same
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String city = r.optString("name", "");
            if (city.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
            StringBuilder label = new StringBuilder(city);
            for (String part : new String[] {r.optString("admin1", ""), r.optString("country", "")}) {
                if (!part.isEmpty() && !part.equals(city)) label.append(", ").append(part);
            }
            if (!seen.add(label.toString())) continue;
            out.add(new String[] {label.toString(), city,
                    String.format(Locale.US, "%.4f", r.getDouble("latitude")), String.format(Locale.US, "%.4f", r.getDouble("longitude"))});
        }
        return out;
    }

    private static String get(String url) throws IOException {
        return new String(getBytes(url, true), StandardCharsets.UTF_8);
    }

    private static byte[] getBytes(String url, boolean redirects) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setInstanceFollowRedirects(redirects);
        c.setRequestProperty("User-Agent", "FreeMyTV");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Night at your location: from sunset to sunrise (like Apple's "Automatic" appearance: the light design darkens).
     * Uses the last weather fetched (it moves ~1 min a day); without data, 6:00 and 18:00.
     */
    boolean isNight(long now) {
        return isNight(Prefs.theme(), now, weather.sunrise, weather.sunset);
    }

    /** The same from what it depends on (SleepOverlay gets these in its Intent). */
    static boolean isNight(String theme, long now, long sunrise, long sunset) {
        if (theme.equals("dark")) return true;
        if (theme.equals("light")) return false;
        long t = Shaders.timeOfDay(now, 0);
        long rise = Shaders.timeOfDay(sunrise, 6 * 60 * 60_000L);
        long set = Shaders.timeOfDay(sunset, 18 * 60 * 60_000L);
        return t < rise || t >= set;
    }

    // MARK: - Formatting

    private static Locale locale() {
        return Ui.locale();
    }

    /** 4.15M · 580.4k · 21.0k · 1.04k · 5.61 (as short as possible; the dollar always with cents). */
    static String price(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 1_000_000) return String.format(locale(), "%.2fM", v / 1_000_000);
        if (v >= 10_000) return String.format(locale(), "%.1fk", v / 1000);
        if (v >= 1000) return String.format(locale(), "%.2fk", v / 1000);
        return String.format(locale(), "%.2f", v);
    }

    /** 4.15M · 416.3k · 12.2k · 1.04k · 531 · 5.05: abbreviated, for the price row on the home screen. */
    static String compact(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 1_000_000) return String.format(locale(), "%.2fM", v / 1_000_000);   // Bitcoin in rubles…
        if (v >= 10_000) return String.format(locale(), "%.1fk", v / 1000);
        if (v >= 1000) return String.format(locale(), "%.2fk", v / 1000);
        if (v >= 100) return String.format(locale(), "%.0f", v);
        return String.format(locale(), "%.2f", v);
    }

    /**
     * $108,234 · $185.20 · R$ 581.234 · 74 264 € (cents only below a thousand), in the configured currency, with the
     * symbol and spaces where the language puts them.
     */
    static String brl(double v) {
        String symbol = currencySymbol();
        if (Double.isNaN(v)) return symbol + "—";
        return Ui.once("brl", v, () -> {
            DecimalFormat f = (DecimalFormat) NumberFormat.getCurrencyInstance(locale());
            DecimalFormatSymbols s = f.getDecimalFormatSymbols();
            s.setCurrencySymbol(symbol.trim());   // our symbols: ICU would write "US$" outside the US
            f.setDecimalFormatSymbols(s);
            f.setMinimumFractionDigits(v >= 1000 ? 0 : 2);
            f.setMaximumFractionDigits(v >= 1000 ? 0 : 2);
            return f.format(v);
        });
    }

    /** The weather in words, from Open-Meteo's WMO code. */
    static String condition(int code, boolean day) {
        if (code < 0) return "";
        if (code == 0) return day ? Ui.tr("Clear") : Ui.tr("night|Clear");
        if (code == 1) return day ? Ui.tr("Mostly Sunny") : Ui.tr("Mostly Clear");
        if (code == 2) return Ui.tr("Partly Cloudy");
        if (code == 3) return Ui.tr("Cloudy");
        if (code == 45 || code == 48) return Ui.tr("Fog");
        if (code >= 51 && code <= 57) return Ui.tr("Drizzle");
        if (code == 65 || code == 67 || code == 82) return Ui.tr("Heavy Rain");
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Ui.tr("Snow");
        if (code >= 95) return Ui.tr("Thunderstorms");
        return Ui.tr("Rain");
    }

    /** "H:24°  L:15°" (in Portuguese, "Máx. 24°  Mín. 15°"), with the given space between the two. */
    static String highLow(Weather wx, String space) {
        return Ui.trf("H:{max}", "max", degrees(wx.max)) + space + Ui.trf("L:{min}", "min", degrees(wx.min));
    }

    private static String currencySymbol() {
        switch (inDollars ? "USD" : CURRENCY) {
            case "USD": return "$";
            case "BRL": return "R$ ";
            case "EUR": return "€";
            case "GBP": return "£";
            case "JPY": return "¥";
            default: return CURRENCY + " ";
        }
    }

    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    static String degrees(double v) {
        if (Double.isNaN(v)) return "—";
        return Math.round(fahrenheit() ? v * 9 / 5 + 32 : v) + "°";
    }

    private static boolean fahrenheit() {
        String unit = Prefs.temperature();
        if (unit.equals("F")) return true;
        if (unit.equals("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }

    /** "+1.2%" / "−0.8%" (a real minus sign); "+1,2 %" in the languages that space the percent sign. */
    static String change(double v) {
        if (Double.isNaN(v)) return "";
        return Ui.once("change", v, () -> {
            NumberFormat f = NumberFormat.getPercentInstance(locale());
            f.setMinimumFractionDigits(1);
            f.setMaximumFractionDigits(1);
            return (v >= 0 ? "+" : "−") + f.format(Math.abs(v) / 100);
        });
    }

    static String hour(long at) {
        return Ui.hour(at);
    }

    /** "Seg", "Mon", "Lun", "Mo", "Пн": the short day of the week, capitalized and without the abbreviation's dot. */
    static String weekday(long at) {
        return Ui.once("weekday", at, () -> {
            String s = Ui.datePattern("format|EEE").format(at);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
            return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Ui.locale()) + s.substring(1);
        });
    }

    /** "Sunday", "domingo", "Sonntag": a day's full name, for TalkBack. */
    static String weekdayName(long at) {
        return Ui.once("weekdayName", at, () -> new SimpleDateFormat("EEEE", Ui.locale()).format(new java.util.Date(at)));
    }

    /** How long ago the data was fetched, if it's stale (no internet): "12 min ago". */
    static String staleness(long at, long every) {
        if (at == 0) return "";
        long age = System.currentTimeMillis() - at;
        if (age < every * 2) return "";
        long minutes = age / 60_000;
        return minutes < 60 ? Ui.trf("{n} min ago", "n", minutes) : Ui.trf("{n} h ago", "n", minutes / 60);
    }
}
