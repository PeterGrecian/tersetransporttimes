# Completed

- [x] the alarm always sounds when you arm it
  - Fixed: Skip immediate threshold if arming within 30 seconds of boundary
  - Fixed: Tap while ringing stops sound but stays armed; tap again to disarm
  - Fixed: Added 15-second grace period after arming

- [x] the bus minute timers should go gray between starting a refresh and getting the data
  - Fixed: added isLoading parameter to DirectionSection and TimeBox composables

- [x] when showing one bus stop, the second bus I think is the wrong direction
  - Fixed: t3.py now fetches from both direction stops (inbound and outbound NaPTAN IDs)

- [x] are we absolutely sure that the stop in Surbiton towards Hook is on claremont rd, stop NK?
  - Verified: 490015165B is "Surbiton Station (Stop NK)" for K2 outbound to Hook

- [x] In trains section, add column for how long till departure in 1/4 minute resolution
  - Added: Shows "in X¼" format with 15-second resolution, updates with data refresh
  - Scheduled departure time shown in gray

- [x] when access to the API fails because of lack of internet connectivity a more friendly warning should be issued "Don't Panic!" Trying again in 10 seconds with a countdown
  - Fixed: Shows "Don't Panic! Trying again in Ns" on API failure with no cached data, auto-retries every 10 seconds

- [x] rename t3 bus to t3 and add a train to the logo
  - Fixed: App renamed from "T3 Bus" to "T3", launcher icon shows "T3" in block letters

- [x] Train delay indicators
  - Fixed: Delayed trains show delay minutes inline (e.g., "17:42+3")
  - Departure time shown in orange when delayed

- [x] UI polish
  - Fixed: Train stop count now only counts up to destination (not beyond)
  - Fixed: Refresh time display works on both bus and train pages

- [x] Bus alarm improvements
  - Fixed: Banner notifications show actual arrival time with vibration
  - Fixed: Alarm state persists across tab navigation
  - Fixed: Auto-disarm when armed bus disappears

# NaPTAN IDs

Parklands:
- 490010781S = inbound (towards Kingston Hospital via Surbiton)
- 490010781N = outbound (towards Hook via Tolworth)

Surbiton Station:
- 490015165A = Stop NC, inbound (towards Kingston)
- 490015165B = Stop NK, outbound (towards Hook)

# TODO

## Bus-train integration
- [ ] Calculate from bus arrival time at Surbiton station + walk time which trains you can catch
- [ ] t3.py (bus) and trains_darwin.py (trains) already run as separate Lambdas - could add a combined endpoint
- [ ] Consider a single `/journey` endpoint that returns both and flags alternatives

