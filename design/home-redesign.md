# Home screen redesign: stage log

Follows the design-principles staged process. This file lives in `design/`, not `docs/`, because
`docs/` is the website that GitHub Pages publishes.

- **Work size:** new screen in an existing product, so stages 1 → 5 (stage 1 writes the job, not
  a new idea: T1).
- **Add-ons:** `data-display.md` (weather, scores, schedules), `native.md` (Android TV), `tv.md` (TVs and glance boards, added 2026-10-29).
- **Current stage:** 3 (look) approved 2026-10-29; build plan below, waiting for the owner's OK.
- **Mocks:** Design canvas "Free My TV home redesign" (claude.ai artifact, private).

## Owner decisions

| Date | Decision |
|---|---|
| 2026-10-29 | Dark mode only. Owner's call for this TV; overrides C6 / N7 (follow the system theme, offer a toggle). |
| 2026-10-29 | Google TV app only; the LG/Samsung web app stays as upstream. |
| 2026-10-29 | Keep TMDB picks. Fire TV is the first shelf tile. |
| 2026-10-29 | Drawer: inputs and shortcuts unchanged, then widget rows the page scrolls through, no sideways scrolling. |

| 2026-10-29 | The app's name is **808 TV**. |
| 2026-10-29 | Option B's rows below the main screen reuse the approved drawer rows. |
| 2026-10-29 | Each widget's no-data state (no network, off-season, no game) is decided card by card at build time. |
| 2026-10-29 | Environment Canada approved as a source: weather alerts and AQHI, hourly, only while the board shows. |
| 2026-10-29 | Only the shelf, the TV sources and Settings take a press; everything else may take any shape, no boxes. |

### Polish pass (2026-10-29, for review)

The owner asked for more "wow" than a plain dashboard, within the TV's limits. Canvas row
"Polish pass" has three boards (daytime, match day, quiet hours) and a details sheet:
- A sky gradient behind the top of the board, following the time of day and the weather.
- Weight contrast within the one family: a thin clock (200) against heavy numbers (800).
- A "Coming up" timeline, three-letter team chips, and a temperature curve with rain bars.
- Leave-by countdown rings around the bus minutes.
- Live cards in the team's gradient; fantasy duel bars.
- In quiet hours, two big leave rings on a night sky, with the shelf tucked into one row.
- Motion: focus grows 8% in 150 ms, minutes roll, a goal flashes amber for 3 s, the live dot
  fades, and the board cross-fades at 6, 11, 4 and 9.

All of it is gradients, flat shapes, paths and text, with motion through transform and alpha
only. No blur, glass, video or photos. Two departures to settle:
- T2 (glass and soft radii) still holds, but the rings and the round live dot are new round shapes.
- The earlier rule that team colors appear only as small crests is loosened: live cards use the
  full team gradient.

### Weather skies and color schemes (2026-10-29, for review)

- **Owner's call:** the sky behind the board follows the weather as well as the time of day. Sunny
  is light blue from the top, rain grey and cloudy, snow white-grey, plus night versions. Clouds,
  rain and snow look natural: placed at random, at three depths, drifting at different speeds.
- How on the TV: clouds are drawn once, blurred, into bitmaps when the weather changes, then
  slid sideways (parallax). Rain and snow are a few hundred lines and dots in three layers. Stars
  are a bitmap with a few twinkling. Sunrise and sunset blend over about an hour.
- **Owner's call:** fonts and colors are open. Canvas board "Color schemes" shows five:
  - Scoreboard night (Atkinson Hyperlegible Next)
  - Pacific (Outfit)
  - Ember (Fraunces + Inter)
  - Aurora (Space Grotesk)
  - Graphite and lime (Barlow Condensed + Inter)

  All are free fonts (OFL), bundled in the APK.
- **Owner's call:** nothing on the home screen fetches or animates while another app is in
  front. `HomeActivity.onPause` → `HomeView.pause()` already stops the frame callback, the tick
  and the data listener, and `HomeData.refreshIfStale` runs only while the screen shows. One gap:
  HomeData's network callback can retry after a failed fetch while the screen is hidden. Gate it
  on the screen being visible. Each new source (buses, sports, fantasy) follows the same rule.
- **Owner's call:** the look is Scoreboard night (Atkinson Hyperlegible Next, blue-black,
  amber) on the weather sky. The sunny sky has no light shafts: clear blue from the top, a soft
  warm corner where the sun is, a few wisps of high cloud. Boards "Sky sunny" and "Sky rain".
  Over the sky, lists sit on a solid panel and secondary text brightens (#E4EAF3) so it keeps
  its contrast (A-rule).
- **Owner's call (approved):** the sunny sky runs light blue at the top to mid blue at the bottom,
  with no dark band. Day rain is a light silver-grey with pale clouds and white rain; night rain
  is near black with a warm city glow, so the two never look alike. On bright skies the clock and
  date get a soft dark halo and a text shadow (A-rule).

### TV and glance-board rules (2026-10-29)

The owner felt the look was close but missing polish. Checked against the portfolio's design
notes (slop tells, luxury research) and new `tv.md` rules, the fixes are mostly removals:
- One dominant item per view (TV6); the weather sky is the signature, everything else quieter.
- Boxes only on things you can press; reading text sits on the sky or a scrim (TV4).
- Each zone its own composition (V2); no "COMING UP" eyebrow (T2); flat colors on tiles.
- Text floor 24 px (TV2): the 20 px footer goes up. Safe area 96 px sides, 54 px top and
  bottom (TV1): the mocks' 80 px sides move in.
- Square corners everywhere; round only for rings and the live dot.
- Open: the Fire TV tile big (A) or a wide shelf tile (B); a staged entrance once a day,
  a 250 ms fade when coming back from an app (TV7).
- **Owner's call:** leave rings on the main screen in the morning (6 to 11) and the evening
  (4 to 9). At night (9 pm to 6 am) the main screen stays calm and the rings sit in the
  rows below. Daytime keeps the slim bus bar.
- **Owner's call:** no temperature graph on the main screen: the number, high and low,
  and one sentence ("Rain from 4 pm"). Two weather widgets in the rows below: today by the
  hour (numbers, no curve) and the next days (one row each, low and high with a range bar
  on a shared scale, after Apple Weather and CARROT; D4).
- **Owner's call (2026-10-29):** only the shelf (Fire TV, apps), the TV sources and Settings take a
  press. Everything else is for reading, so it can take any shape: no cards or containing
  boxes, type and graphics straight on the sky (with a scrim where contrast needs it).
- Budget for the TV (Realtek, about 1.8 GB, `Ui.LEGACY`):
  - Sky and weather bitmaps at half resolution, about 2 MB each, 4 to 6 MB in all.
  - The weather layer animates at up to 30 fps and only while the board is idle. It stops while
    you move around and in quiet hours, and stays still if frames get slow.
  - Fetches total about 12 MB an hour while the board shows, mostly the bus feed.

## Stage 1 · Job (T1)

When I turn on the TV, I want to get to the Fire Stick in one press and see today's weather, my
matches and my fantasy scores at a glance, so I can start watching without hunting through menus or
being interrupted by autoplaying trailers.

It worked when:
- Fire TV is one press from the home screen (OK on the first tile).
- Nothing on the home screen plays video or sound by itself.
- The home screen answers "what's on today, how's the weather, how are my teams doing" with no presses.

### Inventory (real data the screen shows)

- Burnaby weather (Open-Meteo), AQHI for Metro Vancouver NW and weather alerts (Environment Canada).
- Next departures from 2 TransLink stops (GTFS-Realtime, free key).
- Tottenham Hotspur, Premier League (ESPN public feed): Sat Oct 31, vs Crystal Palace, 10:30 am.
- Vancouver Whitecaps, MLS (ESPN): Sun Nov 1, vs Seattle, 12:30 pm.
- F1 Mexico City GP (Jolpica): FP1 Fri 11:30 am, FP2 Fri 3:00 pm, FP3 Sat 10:30 am,
  Qualifying Sat 2:00 pm, Race Sun 12:00 pm (Pacific; DST ends Nov 1).
- 2 Sleeper NFL leagues, week 8 (Sleeper API).
- The Fire TV Stick on HDMI 3; 5 TV apps on the shelf; inputs and shortcuts in the drawer.

### Sample text needing the owner's check (T9)

Weather values, AQHI value, the rainfall warning, live scores, Sleeper scores, records and team
names, bus routes and stops are examples in the mocks. Fixtures and F1 session times are real.

## Stage 2 · Section map (P5, P4, V1)

TV, 1920×1080, remote only (arrows, OK, Back). Safe area 80 px sides, 60 px top and bottom.

| Layer | Section | Container | Actions to reach |
|---|---|---|---|
| Glance | Clock, weather now, the headline line (live beats soon beats next), alert banner when one is active | Top band | 0 |
| Glance | Shelf: Fire TV first, then 5 apps | Horizontal row | 0 to see, 1 (OK) to open |
| Scan | TMDB picks | Feature + list | 0 to see |
| Scan | Drawer: inputs and shortcuts (unchanged) | Rows | 1 (Down) |
| Scan | Drawer: weather 3/4 + buses 1/4 | Split row | 1, then scroll |
| Scan | Drawer: F1 weekend, Spurs, Whitecaps | 3-up row, each its own composition | 1, then scroll |
| Scan | Drawer: 2 Sleeper teams | 2-up scoreboard | 1, then scroll |

- Empty rows hide (off-season, no key set) and show a one-line setup hint instead of a blank card (ST1, H10).
- Each card fails alone and keeps its last good data with its time (ST3, ST5, H9).
- Back or Up closes the drawer; Back never leaves the home screen (H3, N1).

Checkpoint: owner approved the drawer rows on 2026-10-29.

## Stage 3 · Look

Look: "Scoreboard night". One family (Atkinson Hyperlegible Next, built for legibility at a
distance), a near-black blue ground, one ink, square cards and tiles, fully round pills. One
highlight color for focus and "next", red only for Live (with the word). Team colors appear only
as small crests with their three-letter names (C3, C5).

Mocks on the canvas:
1. Option A · Refined: today's layout, dark, with the Fire TV tile, headline line and alert banner.
2. Option A · Drawer: the approved widget rows.
3. Option B · Glance board: a remake where the widgets sit on the home screen itself.
4. Option B · Match day: the same screen on Sunday at 12:40 pm with two live events.

5. Option B2 · Morning: buses and weather first ("Leave in 4 min"), a weekend timeline that marks overlaps.
6. Option B3 · Kickoff soon: from 60 min before a match the big tile becomes the match; spoiler shield hides results you'll watch later.

2026-10-29: owner leans toward Option B. More ideas in that direction, not yet mocked:
- Time-of-day modes: morning (buses, weather), daytime (coming up), evening (picks), match day (live).
- After full time the result stays on the board until the next morning.
- Sleeper "who to watch": which NFL game has the most of your starters.
- Quiet hours: after 11 pm the board dims and live score changes stop.
- Your own photos, dimmed, behind the board on days with nothing on.

Checkpoint: owner picks the Option B variants to keep before stage 4.

2026-10-29 feedback:
- Keep B Glance. B3 (kickoff takeover) dropped.
- Buses always visible on every view: a bottom bar (other ideas welcome).
- Match day: keep the left column; buses at the bottom; weather on the right instead of TMDB, with TMDB further down.
- No "watch" links or OK-to-watch prompts on sports: scores, updates and times only. Fire TV stays its own tile.
- Time of day: morning = buses + weather; daytime = weather + buses + sports; night = buses + sports;
  quiet hours from 9 pm = like morning, dimmed, with bus times brighter.

### Buses (owner's data)

Two destinations; each row is destination, bus, time. Walking time to each stop:

| Destination | Route | Stop | Walk |
|---|---|---|---|
| Edmonds Station | 106 | 52452 | 4 min |
| Edmonds Station | 119 | 52406 | 8 min |
| Edmonds Station | 133 | 52406 | 8 min |
| Metrotown | 119 | 52454 | 7 min |

The 106 also stops at 52406; ignore that and use 52452. Show a "leave by" time (departure minus
walk) and an estimated arrival at the destination from the schedule. No map.

2026-10-29 answers:
- **Time windows:** morning 6 to 11 am, daytime 11 am to 4 pm, night 4 to 9 pm, quiet hours 9 pm to 6 am.
  Match day is daytime with live events.
- **Bus data:** real-time (TransLink GTFS-RT, free key), fetched every 5 min only while the home
  screen shows; the built-in schedule shows first and fills gaps until live data loads. Every time
  says which it is: plain = real-time, clock icon and muted = schedule, plus "updated h:mm" (H1).
  Minutes count down on the TV between fetches (absolute times, not a stale "in 6 min").
- **Next 3 buses** per destination.
- **Leave by = departure minus walk.** The walk times above already include the buffer.
- **Bus layouts by view:** morning and quiet hours use the leave-by clocks (3; quiet adds the
  last bus), daytime and match day the bottom bar (1), night the departure board (2).
- New network source approved by the owner: TransLink's GTFS-RT API.

### TransLink data, checked 2026-10-29

- **Key:** `translink_key` in `config.properties` only (secret, the owner's).
- **Live:** `https://gtfsapi.translink.ca/v3/gtfsrealtime?apikey=…`, TripUpdates for the whole
  region, about 1 MB (1,144 trips at 3:46 pm). No per-stop filter exists; the old per-stop RTTI
  API was retired in 2024. Times are absolute (`departure.time`, Unix seconds). Positions
  (`gtfsposition`) and alerts (`gtfsalerts`) are about 110 KB each.
- **Static:** `https://gtfs-static.translink.ca/gtfs/google_transit.zip`, 16 MB, no key, valid
  2026-09-07 to 2027-01-03 (version `26SEP_20261023`). Used on the computer at build time only.
  It must be rebuilt when TransLink publishes a new one; past the end date the TV shows live times
  only and says the timetable is out of date.
- The feed uses internal ids, not the numbers on the sign:

| On the sign | Feed id | Trips that count |
|---|---|---|
| Stop 52406, Westbound Edmonds St @ Kingsway | `2430` | 119 and 133 to Edmonds Station |
| Stop 52452, Westbound Edmonds St @ Linden Ave | `2476` | 106 to Edmonds Station |
| Stop 52454, Westbound Kingsway @ Walker Ave | `2478` | 119 to Metrotown Station (not the 133 to Holdom) |
| Route 106 / 119 / 133 | `6645` / `32799` / `33292` | match on route id and direction id |

- **Scheduled ride times** (stop to the station's bus bays): 106 from 52452, 3 to 7 min; 119 and
  133 from 52406, 3 to 5 min; 119 from 52454 to Metrotown, 10 to 24 min depending on the hour.
  "There ~" uses each trip's own scheduled arrival, not an average.
- **Live coverage:** the feed only lists trips already under way. The 119 to Metrotown starts at
  Edmonds Station a few stops before 52454, so its live time shows only minutes ahead; until then
  the schedule (clock icon) stands in. That's the designed fallback, not a fault.
- The built-in timetable for the four bus and stop pairs is small (about 1,000 trips).

### Sleeper, checked 2026-10-29

`sleeper_user=iminperu` in `config.properties` (public, no key). User id `872689788547940352`.
Both 2026 leagues are found from it, so no league ids need to be set:

| League | Team | Record after week 7 | Notes |
|---|---|---|---|
| freaky fantasy 2027 (`1397630448380739584`, 8 teams) | Cam Cam dancing | 3–4 | |
| just for vibes (`1323163164086390784`, 10 teams) | Toronto Turf Toes | 12–2 | Plays the league median too: two results a week |

Both are full PPR, with playoffs from week 15. Week 8 matchups have 0.00 until the games start,
so the board shows "not started", not 0–0. Records need the median game counted in league 2.

### Fantasy Premier League (asked 2026-10-29, not yet approved as a source)

The public API at `fantasy.premierleague.com/api/` answers without a key. It's unofficial and
undocumented, so it can change. `bootstrap-static/` is 1.8 MB and holds gameweeks and deadlines
(gameweek 8 finished; gameweek 9's deadline is Sat Oct 31, 4:00 am Pacific). It needs the owner's
FPL team id.

Checked 2026-10-29 with `fpl_team=2980532` (in `config.properties`): team "Richarli XCX".
- `entry/<id>/` (10 KB): gameweek points, total, overall rank, and every league with its rank and
  last week's rank. `entry/<id>/history/` (3 KB): points and rank per gameweek.
- After gameweek 8: 53 points, 532 in total, overall rank 811,864, up from 1,031,958.
- The team is in 13 classic leagues and no head-to-head ones.
- **Owner's call:** the FPL card shows three things only: gameweek points, overall rank and
  Rutgers FC rank (`fpl_league=845137`). Each rank shows whether it went up, went down or stayed
  the same against last week (`entry_last_rank`, and the previous gameweek's `overall_rank`). The
  arrow goes right after the number, with no amount moved and no league size: "811,864 ▲",
  "3rd ▲", "3rd =". The arrow's shape carries the meaning, so color is extra (C-rule).
- **Owner's call:** Sleeper and FPL become one widget, "Fantasy". Canvas board "Fantasy widget"
  has three layouts, each shown on a quiet Thursday and on a live Sunday: one row per team, a tile
  per team, and NFL matchups with an FPL stat strip. **Chosen: layout 3** (NFL matchups, then the
  FPL strip).
- **Owner's call:** overall rank is dropped. The FPL strip has three tiles:
  - Gameweek points ("GW8 · final" or "GW9 · live").
  - Rutgers FC rank with ▲ ▼ =.
  - Between gameweeks, when the next one starts (its first kickoff: GW9 on Sat 5:30 am Pacific),
    with the deadline (4:00 am) under it. It turns amber from 48 h before the deadline. While a
    gameweek is live it shows when that one ends (GW9: Mon 12:00 pm).
  - The times come from `fixtures/?event=<n>` (3 KB) and the deadline from `bootstrap-static/`'s
    `deadline_time`, read at most once a day.
  - During a live gameweek the Rutgers rank keeps last week's value and says "Updates after GW9".
- `bootstrap-static/` (1.8 MB) only for the deadlines: at most once a day.
TransLink stop pages: 52406, 52452, 52454 (`translink.ca/schedules-and-maps/stop/<id>/schedule`).

## Build plan (for the owner's OK)

Each step builds, installs on the TV (192.168.1.71) and gets checked there before the next.
`./build.sh` must end with `Built FreeMyTV.apk`; no key goes in a commit.

1. **Make it yours.** The name 808 TV on screen and in the launcher, credit to Free My TV, and
   `Updates` turned off (it would pull upstream's release over ours).
2. **Fire TV tile.** First on the shelf, flat Fire TV orange, two slots wide, opens HDMI 3.
3. **Weather sky.** Early, so its speed is measured on the TV: the six skies, the day/night
   blend, the 30 fps idle-only animation, and the network callback gated on the screen showing.
   Weather alerts and AQHI (Environment Canada) join the weather card here.
4. **Board layout.** Option B with the time-of-day views (morning, daytime, match day, night,
   quiet hours), Scoreboard night colors, Atkinson Hyperlegible Next bundled, and the rows below
   reusing the drawer rows.
5. **Buses.** A timetable for the three stops built from the static GTFS at build time, and the
   live GTFS-RT feed decoded in Java every 5 minutes while the board shows, with schedule vs
   real-time always marked. Leave-by rings and the next 3 buses.
6. **Sports.** Spurs, Whitecaps and F1 in "Coming up" and as live cards.
7. **Fantasy.** Sleeper (two leagues) and the FPL strip, layout 3.
8. **Empty states and text.** Each card's no-data line, decided card by card, then
   `translate.py` and `tools/check.sh` (needs Python).

## Stage 5 · Build log

### Step 1: make it yours (2026-10-29, on the TV)

- The launcher, the settings screen and every on-screen line say **808 TV**: `Ui.NAME`, and
  `Ui.tr` swaps upstream's "Free My TV" for it, so the 26 catalogues and the code's text keep
  upstream's wording and merges stay easy. Manifest labels and `app_name` say 808 TV.
- Credit: the settings screen's version line reads "808 TV <version> · built on Free My TV".
- `Updates` reads this fork's releases (`PhuruShekar/free-my-tv`), not upstream's. A build from
  this computer never downloads anyway (not `--generic`, its own key); now the check can't
  offer upstream's version either.
- Package name, log tag and the Free My TV mode's internals are unchanged.

### Step 2: Fire TV tile (2026-10-29, on the TV)

- Owner picked option B: the first shelf item, two slots wide, flat Fire TV orange (#FF9900)
  with "Fire TV" in dark ink (#1A1206). OK opens HDMI 3, where the stick is
  (`Rules.FIRE_TV_PORT`).
- No signal on the port: the tile dims and says "No signal", and OK still opens the port
  (HDMI-CEC can wake the stick). "No signal" waits for the catalogues in step 8.
- It can't be lifted or moved, and the other apps can't pass it; the saved shelf order holds
  apps only.
- Focused it grows 8% (`FIRE_GROW`), not the apps' 20%: at double width 20% reached the next
  tile. Corners stay rounded like its neighbors until the board layout squares them all.

### Step 3: weather sky, alerts and air quality (2026-10-29, on the TV)

- The sky replaces the photos behind the board (`WeatherSky`): clear, cloudy, rain and snow, day
  and night colours blended over the hour around sunrise and sunset. Clouds and stars are alpha
  masks at half size, made off the main thread; text is light day and night (dark mode only).
- Motion only while nothing else moves, at about 30 fps; it stops behind All Apps, and for the
  rest of the visit if 30 frames come late. A sky tried from a computer
  (`adb shell settings put global freemytv_sky rain-day`) also moves while you click around.
- Rain and snow are tiles drawn once and slid, two depths, the near one softly out of focus.
  Measured on the TV: 260 lines drawn each frame kept the render thread at 41% of a core; tiles
  at about 10%, with the graphics chip at about 23 ms of the 33 ms a frame has.
- **Owner's call:** drops on the window (beads, drops running down) read as spheres, not rain.
  Taken out; maybe much later, and then only behind the board.
- **Owner's call:** gusts sway all rain and snow now and then (the near layer further), and
  thunderstorms (WMO 95 to 99) flash: a strike, a dip and a weaker flicker, every 12 to 45 s,
  stronger at night. Splashes later. `Rules.gust` and `Rules.flash`, checked in the tests.
- Air quality and alerts from Environment Canada, hourly while a screen shows; the network
  retry after a failed fetch now waits for a screen too.
- Logic with no Android in it is in `Rules` and checked by `tools/test.sh` (53 checks).
- Left: the grey text loses contrast on the light rain sky (needs the soft halo); quiet hours
  come with the board layout (step 4).
