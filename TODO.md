# Completed


# NaPTAN IDs

Parklands:
- 490010781S = inbound (towards Kingston Hospital via Surbiton)
- 490010781N = outbound (towards Hook via Tolworth)

Surbiton Station:
- 490015165A = Stop NC, inbound (towards Kingston)
- 490015165B = Stop NK, outbound (towards Hook)

claude - please document these somewhere else and remove from this file

Journeys
There are 3 locations, home, surbiton and waterloo, and 2 directions home to work and work to home
with surbiton as an intermediate.  

The auto and manual location buttons on the train part are good, and these would be good on the bus too.
when the user is nearest home the bus to surbiton is most relevent
when approaching surbiton from home the train to waterloo is most relevent
- claude - please fill in the remainder of these rules as a record of the logic

# TODO now
1) have auto, and manual controls on busses like trains
2) have a readout of distances from home, surbiton, waterloo on the display, in miles with 1 decimal place, not promenant

## Bus-train integration - this is for much later when all the bugs are shaken out
- [ ] Calculate from bus arrival time at Surbiton station + walk time which trains you can catch
- [ ] t3.py (bus) and trains_darwin.py (trains) already run as separate Lambdas - could add a combined endpoint
- [ ] Consider a single `/journey` endpoint that returns both and flags alternatives

