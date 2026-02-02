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
