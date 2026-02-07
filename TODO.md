# Completed

- [x] Auto and manual location controls on buses (Auto, Home, SUR, WAT buttons)
- [x] Distance readout showing distances from Home, Surbiton, and Waterloo in miles with 1 decimal place

# Journey Logic

## Locations
- **Home** (Parklands): 51.39436, -0.29321 (400m radius)
- **Surbiton Station**: 51.39374, -0.30411 (400m radius)
- **Waterloo Station**: 51.5031, -0.1132

## Journey Modes

### Morning Commute (Home → Work)
1. **When nearest Home**: Bus to Surbiton is most relevant
   - Show outbound buses (towards Hook) from Parklands
2. **When approaching Surbiton from Home**: Train to Waterloo is most relevant
   - Show trains from Surbiton to Waterloo
3. **When at/near Waterloo**: Journey complete

### Evening Commute (Work → Home)
1. **When at/near Waterloo**: Train to Surbiton is most relevant
   - Show trains from Waterloo to Surbiton
2. **When approaching Surbiton from Waterloo**: Bus to Home is most relevant
   - Show inbound buses (towards Kingston) from Surbiton Station
3. **When nearest Home**: Journey complete

### At Surbiton (Ambiguous)
- Could be either direction - show both bus and train options
- User can use manual location override to specify direction

## Notes
- NaPTAN bus stop IDs documented in [NAPTAN_IDS.md](NAPTAN_IDS.md)
- Auto and manual location controls help disambiguate when at intermediate locations

# TODO

## Bus-train integration - this is for much later when all the bugs are shaken out
- [ ] Calculate from bus arrival time at Surbiton station + walk time which trains you can catch
- [ ] t3.py (bus) and trains_darwin.py (trains) already run as separate Lambdas - could add a combined endpoint
- [ ] Consider a single `/journey` endpoint that returns both and flags alternatives

