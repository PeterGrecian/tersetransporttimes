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
- **Surbiton station to the Surbiton K2 stop is ~5 min on foot.**
- **Surbiton station to home on foot**: ~10 min from the back exit, ~15 min
  from the front exit.
- Other trips start the same way: usually to Waterloo, sometimes Clapham
  Junction or Vauxhall.

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

Mostly the Waterloo & City line. The 26/76 bus only on a nice day, or when it
hasn't been used for a while. Low priority: a hint, not a decision.

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
