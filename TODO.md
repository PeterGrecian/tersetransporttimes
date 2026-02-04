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

# NaPTAN IDs

Parklands:
- 490010781S = inbound (towards Kingston Hospital via Surbiton)
- 490010781N = outbound (towards Hook via Tolworth)

Surbiton Station:
- 490015165A = Stop NC, inbound (towards Kingston)
- 490015165B = Stop NK, outbound (towards Hook)

# TODO

## Train delay indicators
- [ ] Delayed trains should show as orange, cancelled as red flashing

## Slack integration for disruption alerts
- [ ] Notify Slack when trains are cancelled or significantly delayed
- [ ] Monitor commute windows: outbound from 9am, return from 5pm
- [ ] Consider threshold for "significant" delay (e.g. 5+ mins?) before alerting
- [ ] Darwin API already returns `cancelled` and `delayMinutes` fields - ready to act on

## Bus-train integration
- [ ] it should be possible to calculate from the bus API arrival time at Surbiton station and how long it takes me to get to the platform from the bus stop which trains I can catch.
- [ ] t3.py (bus) and trains_darwin.py (trains) already run as separate Lambdas - could add a combined endpoint
- [ ] Consider a single `/journey` endpoint that returns both and flags alternatives

 when changing to train page it does not say how long it took to refresh
rename t3 bus to t3 and add a train to the logo

when access to the API failes because of lack of internet connectivity a more friendly warning should be issued "Don't Panic!"  Trying again in 10 seconds.  with a countdown


the return journey from Waterloo is weird.  Some have 11 stops!  The departure time is ok, its not there and back is it?  The tab sould be called "Waterloo" not Sur"  maybe WAT.  

