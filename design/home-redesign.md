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

### Step 4: board layout, design pass (2026-10-29, for review)

Canvas row "Step 4 · Board layout": daytime, the rows one press down, and quiet hours, as they
look once this step is built and before buses (step 5) and sports (step 6) fill the middle.
- Main screen: clock top left (the dominant item), weather top right (temperature, one sentence,
  air quality), the alert under it when one is on, then sky, then the shelf. Nothing in the
  middle until steps 5 and 6.
- Square corners on every tile, no glass panel behind the shelf (TV4: the tiles are the
  pressable things, the panel was a box around them).
- **Owner's call:** the three buttons (Live TV, HDMI Inputs, Settings) stay at the top right, square, with the weather under them.
- **Owner's call:** one press down, the drawer's row as today: TV sources, then Shortcuts (All Apps, Home Settings, TV Settings). TV sources list what has a device first: HDMI 3, Live TV, then the empty HDMI 1 and HDMI 2, dimmed. Then picks, then the weather rows.
- Quiet hours (9 pm to 6 am): the same places, text dimmed to about 70%, the apps you're not on
  at 50%, the sky still (no drift, no rain or snow motion, no lightning), the focused tile at
  full strength.
- **Owner's call:** keep Inter (already in the APK), with its alternates that tell l and I apart (cv05, cv08).


### Step 4a: the main screen (2026-10-29, on the TV)

- The board's top: a thin 150 px clock at the top left with the weekday and date beside it; at the top
  right the three square buttons, and under them the weather (temperature, condition with high and low,
  one sentence when rain or snow starts or stops in the next 12 hours, the air), then the alert.
- The main screen no longer shows picks or Continue Watching (`HomeView.BOARD`); square tiles with a white
  ring on focus, no glass behind the shelf; square drawer tiles and cards.
- TV sources sorted by `Rules.sourceOrder`; checked on the TV: Fire TV Stick (HDMI 3), Live TV, HDMI 1, HDMI 2.
- Quiet hours (`Rules.quietHours`, 9 pm to 6 am): the top at 70%, the tiles you're not on at half, the sky
  still. Seen on the TV at 11:44 pm.
- Tests: 77 checks. Left for 4b: the picks row under the sources; the new strings ("Rain from {time}" and
  the rest) wait for the catalogues in step 8.

### Step 4b: the picks row (2026-10-29, on the TV)

- The picks ("For You", the existing string) are a row in the drawer under the TV sources: the same cards as the
  old top of the screen (the wide focused one, the others as posters), square, 400 px tall. Down from the
  sources scrolls the drawer up 634 px (`drawerScroll`) so the row and the weather card under it both show; Up
  goes back to the source nearest the card, Back closes the drawer. OK opens the pick as before.
- The picks no longer change the background: the weather sky stays behind the board.
- Checked on the TV: Down, Right, Up and Back; no errors in the log. Without a TMDB key there's no row and the
  weather card stays right under the sources.

### Step 4c: owner's fixes (2026-10-29, on the TV)

- **Owner's call:** the HDMI 3 tile is the size of the apps and grows like them. It's named and coloured after
  what's on the port (`Rules.portTile`): "fire tv" in lowercase on Fire TV orange, "Switch" on Nintendo red, else
  the device's name or "HDMI 3" on a dark tile. The last name a device gave is kept for when it's asleep. It still
  stays first on the shelf.
- The picks keep their pictures' shape: the wide card in 16:9, the posters in 2:3, and cover images crop instead
  of stretching (that also fixes the old layout's wide card).
- Row names ("Inputs", "Shortcuts", "For You") at 32 px, near white; the shelf lines up with the clock's margin.
- **Owner's call:** the shelf's first tile shows the HDMI port that's on, HDMI 3 first, then 1, then 2
  (`Rules.shelfPort`); with nothing on, HDMI 3, dimmed. It follows the TV's input changes (the shelf reloads).
- The Fire TV tile's name is a wordmark: "fire" with a small raised "tv" (24 px, its top level with the f's).
- HDMI 3 carries two HDMI-CEC devices on this TV (the JBL BAR 5.1 on ARC and the Fire TV Stick): a port's name
  now prefers a player over a soundbar or receiver (`Rules.soundOnly`).

### Step 5: buses (2026-10-30, on the TV)

- **Owner's calls:** one timeline per place (Edmonds Station, Metrotown), across, on a shared 45-minute scale, with a
  mark per bus at its leave time and notches every 15 minutes; "Leave 7:49" with "Arrive 7:58" under it; every number
  in the day line has its unit; the rows below get a Buses stop of their own (the remote can rest there).
- Timetable: `tools/BusTimetable.java` writes `app/assets/buses.txt` (8 KB) from TransLink's static GTFS: one day of
  times per day type (weekday, Saturday, Sunday) for the four bus and stop pairs, and the holidays that run another
  day's buses. Valid to 2027-01-03; run it again on TransLink's next one.
- Live: TransLink's GTFS-RT every 5 minutes, only while the home screen shows, decoded by hand (`Transit`); a live
  time replaces its timetable bus (by its delay within 90 s, else the nearest within 4 min). Older than 15 min: all
  timetable. The key is in `config.properties` only and never logged; no redirects are followed.
- Views (`Rules.busView`): timelines 6 to 11 am and 4 to 9 pm, one line over the shelf 11 am to 4 pm, one bright line
  in quiet hours. `adb shell settings put global freemytv_buses 0|1|2` tries one.
- Crowded buses: a route that would run into the one before goes under the bar; the minutes then fill what room is
  left there, soonest first (the first bus's time is also its Leave line). Nothing is ever drawn over another label.
- Focus on the Buses row: no ring (it touched the rows around it, and there's nothing to press), the header turns into
  a white pill and the rows around it fade to 70%.
- Fix from the step 4 review: with the Fire TV Stick asleep, the JBL soundbar alone on HDMI 3 named the shelf tile
  "JBL BAR 5.1" and was saved over the stick's name; a sound-only name now counts as none (`Rules.portDevice`).
- Tests: 128 checks.
- Left for step 8: the new strings ("Leave {time}", "Arrive {time}", "Next bus", "Buses", "Timetable"…).

### Step 6: sports (2026-10-30, on the TV)

- **Owner's calls:** the four step 6 boards; ESPN's public feed approved as a new source. ESPN's F1 scoreboard has the
  whole weekend, the session that's on, its lap and the running order, so Jolpica isn't used.
- Data (`HomeData.refreshSports`, only while the home screen shows): each team's page (next game, standing) and the F1
  scoreboard every 30 minutes, every minute while something is on or starts within 15; the results (the schedule, up
  to 470 KB for the Whitecaps) every 6 hours and when a game ends. Premier League and MLS games only (ESPN lists cups
  under other leagues).
- What shows (`Sports`): a game on, else a result until 6 am the next morning, else the next game; F1 the session on,
  else the race's result, else the next session. Live cards first, then the soonest, then results.
- Main screen: three cards in the middle in the day (11 am to 4 pm); in the morning and evening only while something is
  on, and the bus timelines then become the bus line. Quiet hours: one dim line over the bus line while a game is on.
- One press down: a Sports stop under Buses (same pill focus, the rows around fade): standing, the last 5 results,
  last and next game; the F1 weekend with the session on in red, the next in amber, the ones run in grey.
- Review fixes (2026-10-30): a postponed game or a live game with no score never shows -1; long team names are cut to
  the card; ESPN's team page names the *next* fixture as soon as a game starts (checked with a live Spanish game), so a
  game in play is read from the league's scoreboard (`dates=` is the US Eastern day; from 15 minutes before to 4 hours
  after kickoff) and a game just finished joins the results at once; polling every minute stops for a start time that
  passed more than 3 hours ago; ESPN's JSON is read by `SportsFeed` and tested against saved feeds (`app/test/feeds`,
  needs org.json's jar in `tools/lib`).
- Tests: 223 checks (SportsTest 36, SportsFeedTest 28).
- Left for step 8: the new strings ("Sports", "LIVE", "Full time", "Next: {team}, {when}", "Today {time}"…), and the
  W/D/L letters.

### Step 7: fantasy (2026-10-30, on the TV)

- **Owner's call:** the Fantasy row only, no fantasy on the main screen (the middle already holds the sports cards).
- Row: a Fantasy stop under Sports (same pill focus, the rows around it fade): one card per Sleeper team (record and
  place in the league, this week's score against the opponent's with a bar, or "vs {opponent}" and "Week 8 · not started"
  before the games, and last week's result) and the FPL strip (gameweek points, Rutgers FC rank with ▲ ▼ =, when the next
  gameweek starts and its deadline, amber from 48 hours before; live: when it ends, last week's rank held and "Rank
  updates after GW9").
- Data (`HomeData.refreshFantasy`, only while the home screen shows; `sleeper_user`, `fpl_team` and `fpl_league` in
  `config.properties`, all public and read-only): every 5 minutes; Sleeper's leagues, rosters and names once an hour,
  FPL's gameweeks (1.8 MB, only the `events` array is parsed) once a day, the fixtures when the gameweek changes.
- Sleeper's roster record already counts the league-median game (12–2 after 7 weeks), so it's used as it is.
  "Players still to play" isn't shown: Sleeper doesn't say which players have played.
- Tests: 223 checks (FantasyTest 31).
- Left for step 8: the new strings ("Fantasy", "points", "Week {n} · not started", "GW{n} starts {when}", "Deadline
  {when}"…) and the ordinals ("3rd", English only for now).

### Step 8: empty states and text (2026-10-30, on the TV)

- **Empty and failed states, card by card** (nothing new to draw; each already had its line, one was added):
  - Buses: no timetable (or no key) hides the row; "No buses soon"; "Timetable out of date"; live data older than 15
    minutes says "Timetable · live data from {time}".
  - Sports: before ESPN's first answer the row is hidden; "No games soon" and "No sessions soon" per card; when a fetch
    fails the last good data stays under its "Updated {time}".
  - Fantasy: no Sleeper league for the season and no FPL team hides the row; a team with no opponent says "Bye week"
    (new); the same "Updated {time}".
- **Text: English only (owner's call).** Translating 60 new strings into 24 languages wasn't worth it, so every catalogue
  except `en.json` is deleted (they stay in git history). `Ui.languages()` lists only what's in `assets/i18n`, so the
  language row has one choice and "Automatic" lands on English. `Ui.tr` and the catalogue code stay as they were (a
  catalogue added later is picked up); `tools/translate.py --check` passes (446 keys) and `tools/check.sh` no longer
  stops on translations. The APK is 0.2 MB smaller.
- Strings built with `a ? "x" : "y"` inside `Ui.tr` aren't seen by `translate.py`: `where()`, `when()` and the FPL
  subtitle now call it once per text.
