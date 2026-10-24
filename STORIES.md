# T3 stories

How Peter actually uses T3, and what it should show at each moment. Collected
2026-10-23 (mobileapps strand). This is the spec for the mode-selection logic;
screens are ASCII sketches, not designs: the existing look is fine.

## Givens

- **Work**: 88 Wood St, EC2. What matters is being near Waterloo, or in the
  City / near Bank, not the office itself.
- **Days vary.** Peter goes in before noon and returns in the afternoon. Mode
  should come from **location and movement**, not a timetable of days.
- **At work by about 11:00**: a very soft deadline. Rank options, don't hide
  ones that miss it.
- **60+ concessions**: buses free after 09:00, trains after 09:30 on weekdays.
  So the morning trip starts on a 09:00+ bus and a 09:30+ train.
- **Train preference**: the fast non-stop (the 09:47, as remembered) is best;
  a 2- or 4-stopper is bearable; **avoid the 7-stopper**. `/trains` already
  returns `stops` per service.
- **Parklands to Surbiton on foot is ~10 min.** Walk when that beats the bus.
  The app could learn the real walk time from GPS rather than hardcode it.
- **Home to the Parklands K2 stop is ~3 min on foot**, and **K2 + walk from
  the Surbiton stop to the platform is ~12 min** (Peter, 2026-10-24).
- **Surbiton station to the Surbiton K2 stop is ~5 min on foot.**
- **Surbiton station to home on foot**: ~10 min from the back exit, ~15 min
  from the front exit.
- Other trips start the same way: usually to Waterloo, sometimes Clapham
  Junction or Vauxhall.

## Measured from Google Timeline (2022 to 2026-10, aggregates only)

From the 6a's on-device Timeline export (raw file private on pip and backed up to
`s3://backup-peter/timeline/`, never in git;
`~/private/timeline/commute.py` prints aggregates only). 81 trips in, 93 home.

| Leg | Median | IQR | Notes |
|---|---|---|---|
| leave home → train departs | 17 min | 13–26 | bus 17, walk 18, waits included |
| SUR → WAT train | 24 min | 21–27 | |
| WAT → work (Wood St/Bank) | **21 min** | 18–27 | W&C + walk 41/56, bus 7, walk 6 |
| leave work → train departs | 35 min | 22–60 | the long tail is the pub decision |
| WAT → SUR train | 21 min | 18–26 | |
| train arrives → home | 21 min | 14–26 | **walk 16 vs bus 23** |

Out: leaves home mostly 09:00–10:00 (49/81). Back: train home mostly
18:00–21:00, home 19:00–22:00. Days: Tue 25, Fri 24, Thu 15 (of 81). Trips
are increasing: 11 in 2024, 23 in 2025, 39 so far in 2026.

**Home → 88 Wood St, past year (to 2026-10-24): 51 direct journeys.** Timeline
labels Wood St itself as WORK. Door to door **median 66 min** (IQR 60–75,
43–100): leave home → train 18, train 24, off the train → work 21. Surbiton →
Waterloo 45/51 (once via Vauxhall). To the station: bus 28, walk 23. Left home
09:00–10:00 on 43/51; arrived 10:00–11:00 on 35, after 11:00 on 16 (the
deadline is soft). Days: Fri 18, Tue 17, Thu 9, Wed 5, Mon 2.

**Waterloo → work by mode (2022 on):** Underground (W&C) n=41, **median 21 min**
(IQR 20–25); bus n=7, **36** (32–50); walk n=6, 34. So the 26/76 costs about
**15 min** more than the W&C: S6's "bus on a nice day" is a deliberate 15-minute
choice, and the screen should say so ("bus +15").

**On-foot transfers (2022 on, Timeline; most include waiting):** walk home →
Surbiton **11.9 min** (IQR 10.3–15.2, walk only, n=90); K2 off at Surbiton →
train moving 7.1 (walk + platform wait); train in at WAT → W&C moving 5.9;
W&C ends → at work **11.3** (deep exit + walk from Bank); train in at SUR →
K2 moving **14.4** (walk ~5 + a long wait) vs walking home **14.9** whole.
Home → K2 is unreliable (Timeline merges the 3 min walk into the bus).
**Inbound Surbiton figures are skewed by pub stops** (Peter); only the walk-only
segment (13.3 min, n=72), mostly rear-exit walks, is a clean walking time.
**Precision:** timestamps are to the second, but raw positions were only every
~4 min (median gap 228 s, last month), so single legs are ±2–4 min and medians
over dozens of trips ±~1 min.

**Train time by stopping pattern.** Timeline train legs cluster round three
values, consistent with the 1-, 4- and 7-stoppers, but overlapping, and about
3–4 min shorter than the timetable because Timeline only counts the moving
part:

| | Fast | 4-stop | 7-stop |
|---|---|---|---|
| timetable SUR→WAT (`/trains`, 2026-10-24) | 22 | 25–27 | 30 |
| Timeline SUR→WAT clusters | ~17–19 | ~21–24 | ~26–28 |
| timetable WAT→SUR | 15 (non-stop) | 24 | 29 |
| Timeline WAT→SUR clusters | ~16–19 (peak 17) | ~21–24 | ~26–28 |

Good enough to say roughly how often each kind was taken, not to label a single
trip. T3's own log can label trips exactly: it knows which train was on the
board when Peter boarded.

So: arrival at work = WAT arrival + 21 min. On the train home (I2), walking is
the default; the K2 earns its place only when one is due about as you reach the
stop, or it is raining.

## S1. Leaving home in the morning

At home, heading out. Today: K2 times only. Wanted: the **bus + train chain**:
which train each bus catches, given the 09:00/09:30 concession times, the
11:00 deadline and the stopper preference. Plus "walk instead" when walking
catches the same or a better train.

```
 ┌ HOME → WORK ─────────────────────── dry ┐
 │ K2  4 min ──▶ 09:47 SUR→WAT  fast  ★     │
 │ K2 16 min ──▶ 10:02 SUR→WAT  2 stops     │
 │ walk 10 min ─▶ 09:47 too (bus not needed)│
 │ arrive Wood St by ~10:30   (limit 11:00) │
 └──────────────────────────────────────────┘
```

**Worked example (Peter, 2026-10-24, about 09:49):** K2 due in 9 min. The
walk to the stop takes 3, so there's time. Bus at ~09:58, plus ~12 min, puts
him on the platform at ~10:10. The trains: a 1-stopper and a 4-stopper at
10:12, and a 1-stopper at 10:18. So the screen should say the **10:12 is
catchable with ~2 min spare** and the **10:18 is the safe one**.

**Layout (Peter, 2026-10-24): one row per bus, with the trains it enables.**
Only the next one or two buses. Each shows its time and the trains you can
make from it (departure time, number of stops). **Walking is not a mode to
detect**: it only matters when walking reaches a train that no bus does, and
such trains are shown greyed and marked **w**. Same 09:49 example; walking
~10 min would reach the platform by ~09:59:

```
 ┌ HOME → WORK ───────────── 09:49  5% ┐
 │ w 10:01 ·4  (late 09:57)             │  greyed: walk only
 │                                      │
 │ K2  9 min → 10:12 ·1 ★  10:12 ·4     │
 │             10:18 ·1                 │
 │ K2 23 min → 10:27 ·4                 │
 └──────────────────────────────────────┘
   ·N = stops   ★ = best   5% = service health
```

The 7-stopper (10:01) is left out as a choice (it's the one to avoid), but still
counts towards service health.

This needs **new UI** (bus and train info combined on one screen), not just
new selection logic.

**Screen H mock-up v2 (2026-10-24, illustrative 09:13 weekday):**

```
 ┌────────────────────────────────────┐
 │ HOME → WORK         09:13    ● 3%  │
 │                                    │
 │ walk now                     grey  │
 │   w 09:27 ·4  £                    │
 │                                    │
 │ K2  6 min · leave in 3             │
 │   09:42 ·1 ★   work ~10:25         │
 │   09:42 ·4                         │
 │                                    │
 │ K2 18 min · leave in 15            │
 │   09:48 ·1                         │
 │   09:57 ·4  +3                     │
 │                                    │
 │ refresh in 23s ↻          v1.6     │
 │ ────────────────────────────────── │
 │      K2 Buses        SUR Trains    │
 └────────────────────────────────────┘
```

Alternative (one train list, each with how to reach it):

```
 │ 09:27 ·4  £   walk now       grey  │
 │ 09:42 ·1 ★    K2 6, leave 3  10:25 │
 │ 09:42 ·4      K2 6                 │
 │ 09:48 ·1      K2 18                │
 │ 09:57 ·4 +3   K2 18                │
```

**Screen H rules (decided 2026-10-24):**
- ★ = earliest **Waterloo arrival** among catchable trains (a fast 10:18 can beat a 10:12 4-stopper).
- Catchable = on the platform **≥ 2 min** before the *expected* departure.
- Each train appears once, under the latest bus that still makes it; walk-only trains greyed with `w`.
- **Outside the 60+ concession** (weekday trains before 09:30, buses before 09:00): shown **greyed with £**, not hidden.
- Timings: home → K2 stop 3, K2 + walk to platform 12, walk home → platform 12 (measured 11.9).
- 7-stoppers are not offered, but count towards health %; `/trains` asked for 10 departures.

**Trains you can't make are not listed, but they count.** At 09:54 the 09:48
and the 09:57 (expected 10:01, 4 min late) were already out of reach. They are
irrelevant as choices, but they are the only evidence of the service's
health. Peter's reading: "about 5% broken". So T3 should fold them into **one
service-health figure** shown beside the list, rather than list them.

Draft score (to tune against what Peter calls 5%): over the last ~45 min of
departures plus the ones still to come, each train scores `min(delay / 30 min,
1)`, and a cancellation scores 1. Weight recent trains most (the trend matters,
see S3's clearing-up rule), and show it as "% broken". Two trains a few minutes
late, then on time = single digits. A cancellation of your fast train is loud
on its own, whatever the score.

**Leaving home (H → O1):** once Peter is on the bus or walking, i.e.
moving towards the station, the buses have done their job. The display drops to
**trains only**, each with an estimated arrival at work:

```
 ┌ TO SURBITON ──────────── 10:03  5% ┐
 │ platform ~10:10                     │
 │ 10:12 ·1 ★  WAT 10:31  work ~10:50  │
 │ 10:12 ·4    WAT 10:36  work ~10:55  │
 │ 10:18 ·1    WAT 10:37  work ~10:56  │
 └─────────────────────────────────────┘
```

(WAT and work times in the mock are illustrative.) Arrival at work = the train's Waterloo arrival (already in `/trains`) plus the
Waterloo → Wood St leg. That leg is **21 min** (median of 56 Timeline
trips, IQR 18–27).

## S2. On the train in (Surbiton → Waterloo)

Moving fast along the line, away from home. Next useful thing: the Waterloo →
work leg (S6).

## S3. Leaving work: the pub decision

At work (City / near Bank / Waterloo), about to leave. The choice: pub in the
City, pub at Waterloo, pub at Surbiton, or straight home, **depending on how
long the wait is**. Crucial: **if Peter's train is disrupted** (his train only,
not the network in general), he dawdles home. So disruption must be loud.

**Disruption rule**: a delay of **5 min or more** signals disruption, but if
the *most recent* services are running on time it is clearing up. So judge
the trend across recent departures, not one train.

```
 ┌ WORK → HOME ─────────────────────────────┐
 │ WAT→SUR 17:12 fast   on time   ★         │
 │ WAT→SUR 17:20 4 stops +6 min             │
 │ next fast after 17:12: 17:42 (30 min gap)│
 │ ⚠ 17:12 CANCELLED → take your time       │
 └──────────────────────────────────────────┘
```

## S4. On the train home: which exit at Surbiton

Motion along the line **towards home** should switch to the K2 from Surbiton
(Hook direction). On the train Peter chooses:

- **back exit**, nearer home, and walk;
- **front exit**, and the pub;
- **front exit**, ~5 min walk to the K2 stop, and the bus.

**K2 wait and rain decide it.** Show K2 times *as catchable after the 5 min
walk*, plus rain. Rain **right now** is enough; rain at arrival is a
possible refinement.

```
 ┌ ON TRAIN → SURBITON ────────── 🌧 rain ──┐
 │ K2 from Surbiton (5 min walk to stop)    │
 │   7 min  ✓ catchable                     │
 │   3 min  ✗ gone before you reach it      │
 │   21 min                                 │
 │ ▶ front exit, get the bus                │
 └──────────────────────────────────────────┘
```

## S5. Off-commute trips

Same start as S1, but the destination may be Waterloo, Clapham Junction or
Vauxhall. The train list should not assume WAT is the only stop that matters.

## S6. Waterloo → work

The **Waterloo & City line is the default**: 41 of 54 measured trips, median
21 min. The 26/76 is a rarity (Peter, 2026-10-24) and costs ~15 min more, so it
is at most a small hint ("26/76 +15") on a fine day, never a decision.
Weather does not choose the mode here.

## Stage table (draft 2026-10-24)

The journey as a state machine. **Location** narrows the stage; **direction of
travel and the previous stage** settle which way you're going; time of day only
breaks ties (before 13:00 = outbound). Speed classes: still < 1 m/s, walk
1–2.5, bus 2.5–8 with stops, train > 12 sustained near the line.

| Stage | Where | Direction | Shows | Next stage when |
|---|---|---|---|---|
| **H** Home | ≤ 400 m of Parklands, still | out (or none) | Chain: next K2s → the train each catches (fast ★, 2/4-stop ok, 7-stop dimmed), "walk instead" if it catches the same train; respects 09:00 bus / 09:30 train | leaves home radius moving towards Surbiton → **O1** |
| **O1** To Surbiton | between home and station, moving towards it (bus or on foot) | out | **Trains only**: SUR→WAT you can still make at your current speed, each with an **estimated arrival at work** | ≤ 150 m of Surbiton station → **O2** |
| **O2** Surbiton, outbound | ≤ 150 m of station, arrived from home | out | SUR→WAT departures: platform, stops, delay; fast one highlighted | train speed along the line, moving away from Surbiton → **O3** |
| **O3** Train in | near the SUR–WAT line, train speed, distance to WAT falling | out | Arrival ETA at WAT (CLJ/VXH if chosen); then W&C next trains + rain, 26/76 as a hint | ≤ 500 m of Waterloo, slow → **O4** |
| **O4** Waterloo, outbound | ≤ 500 m of WAT, arrived by train | out | W&C to Bank next trains; 26/76 if dry/nice; rain now | ≤ 1 km of Bank / Wood St → **W** |
| **W** Work / City | central London, not at WAT, still for a while | in (next) | WAT→SUR: next fast train, wait, **disruption trend** (≥ 5 min late = warning, clearing if recent ones on time); W&C from Bank. The pub decision: City / WAT / Surbiton / none | moving towards WAT, or ≤ 500 m of WAT → **I1** |
| **I1** Waterloo, home | ≤ 500 m of WAT, arrived from W (or after 13:00) | in | WAT→SUR departures: platform, stops, delay, disruption loud | train speed, distance to WAT rising → **I2** |
| **I2** Train home | on the line, train speed, nearing Surbiton | in | **K2 from Surbiton stop**, only those catchable after the 5 min walk; rain now; exit choice: back exit walk ~10 min / front exit pub / front exit + bus (~15 min walk) | ≤ 150 m of Surbiton, slow → **I3** |
| **I3** Surbiton, home | ≤ 150 m of station, arrived by train | in | Same K2 countdown + walk-vs-wait | ≤ 400 m of home → **H** (done) |

Off-commute trips run the same stages with a different train destination (CLJ,
VXH). **Unknown** (no fix, or nowhere on the map): show the last stage, greyed,
with the manual buttons. Manual override always wins until the next stage
change; every transition and override is logged (GpsLog `screen`/`refresh`
rows) so the triggers can be tuned against real traces.

Open questions for the table:
- ~~O1 by bus vs on foot~~: settled, no detection needed at H. Walking is shown
  as a `w` option only where it reaches a train no bus does. (O1 may still
  use speed for its platform ETA.)
- Surbiton arrivals with no history (app opened at the station): time of day,
  or both directions side by side?
- W: how long still before "at work" (vs passing through the City)?

## Known problems and wanted features

- **The automatic display choice doesn't work properly** (Peter, 2026-10-23).
  Observed from the code: at work it opens on Parklands buses; on the train
  home it shows Parklands, not Surbiton, buses; time and direction of travel
  are not used at all. Peter knows of no other cases.
- **Speed and direction have been unreliable**: `isOnTrain` takes one fix's
  provider speed (>10 mph) and nearness to a straight WAT–SUR line; bearing
  comes from two fixes 30 s apart, and fixes are only polled while the app
  runs. S2 and S4 depend on this, so it needs smoothing over several fixes
  and logged traces to test against. **Since v1.1 (2026-10-23) every fix is
  logged** (`GpsLog.kt`; tap the grey distance readout to share the CSV).
- **A dual bus + train display** (S1, S4) is the natural extension.
- **Learn walk times from GPS** (Parklands ↔ Surbiton, station ↔ K2 stop).
