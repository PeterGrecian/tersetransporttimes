# Completed

- [x] the alarm always sounds when you arm it
  - Fixed: removed `+ ALARM_INTERVAL_SECONDS` from lastAlarmThreshold calculation

- [x] the bus minute timers should go gray between starting a refresh and getting the data
  - Fixed: added isLoading parameter to DirectionSection and TimeBox composables

- [x] when showing one bus stop, the second bus I think is the wrong direction
  - Fixed: t3.py now fetches from both direction stops (inbound and outbound NaPTAN IDs)

- [x] are we absolutely sure that the stop in Surbiton towards Hook is on claremont rd, stop NK?
  - Verified: 490015165B is "Surbiton Station (Stop NK)" for K2 outbound to Hook

# NaPTAN IDs

Parklands:
- 490010781S = inbound (towards Kingston Hospital via Surbiton)
- 490010781N = outbound (towards Hook via Tolworth)

Surbiton Station:
- 490015165A = Stop NC, inbound (towards Kingston)
- 490015165B = Stop NK, outbound (towards Hook)
in trains section, add column for how long till departure in 1/4 minute resolution
''
