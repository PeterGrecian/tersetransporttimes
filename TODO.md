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

## Bug fixes

- [ ] **500 on end-of-service**: When TfL returns no K2 buses (e.g. after midnight), `t3.py` returns HTTP 500 `{"error": "No K2 buses found"}`. Should return 200 with `{"seconds": []}`. Fix is in place locally (line 82), needs deploying.

## Performance optimizations
- [ ] **Bus API efficiency**: Currently t3.py always fetches BOTH inbound and outbound bus times (2 TfL API calls), even when the app only displays one direction based on location. Could add a `direction` query parameter to only fetch what's needed and reduce API calls by 50%.
- [ ] **Pre-fetch optimization**: Both bus and train screens now cache data per location (Auto/Home/SUR/WAT), but first visit to each location requires a network fetch. Could pre-fetch other common locations in background after initial load (e.g., after fetching SUR trains, pre-fetch WAT trains). Requires proper coroutine scoping to avoid blocking main thread.

## Waterloo → Work leg (not yet implemented)

The final leg of the commute: Waterloo to work (88 Wood St, EC2 area). Two options:
- **Waterloo & City line** → Bank, then walk (~10 min journey, no changes)
- **Bus 26 or 76** → Aldwych / City area (slower but above ground, no interchange)

When near Waterloo in the morning, the app currently shows nothing useful for this leg.
Needs: TfL Tube API (different from bus API), or bus arrivals for stops near Waterloo for routes 26/76.

## Multi-user / configurability

Currently all stops, routes and locations are hardcoded for one commute. To make T3 useful for others:
- Needs a one-time setup screen (stops, routes, home location)
- Main screen stays zero-interaction once configured
- Tension: setup adds complexity, but only visited once
- Not a priority until the app is stable and worth sharing

## Bus-train integration - this is for much later when all the bugs are shaken out
- [ ] Calculate from bus arrival time at Surbiton station + walk time which trains you can catch
- [ ] t3.py (bus) and trains_darwin.py (trains) already run as separate Lambdas - could add a combined endpoint
- [ ] Consider a single `/journey` endpoint that returns both and flags alternatives

