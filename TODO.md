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
3. **When at/near Waterloo**: Tube vs bus to work
   - If raining: Waterloo & City line to Bank
   - If dry: Bus 26/76 from Waterloo (5 min slower but above ground)
   - Weather from GCP Weather API

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

| Option | Route | Time | Notes |
|---|---|---|---|
| Waterloo & City line | → Bank, walk | ~10 min | Underground, no changes |
| Bus 26 or 76 | → Aldwych / City | ~15 min | Above ground, no interchange |

**Preference: bus when dry, tube when raining** — happy to spend 5 extra minutes above ground unless it's raining. T3 should show the preferred option prominently, with rain as the deciding factor.

This is a good GCP entry point:
- **GCP Weather API** — current precipitation at Waterloo postcode → is it raining?
- **TfL Unified API** — bus arrivals for routes 26/76 from Waterloo stops; tube departures from W&C platform
- **Decision logic** — if raining: highlight tube; if dry: highlight next bus; show both with a rain/dry indicator

## Bus-train integration — good GCP entry point, tackle alongside Waterloo leg

- [ ] Combined `/journey` Lambda: bus arrival at Parklands → catchable trains from Surbiton
- [ ] Walk time Parklands bus stop → Surbiton platform: ~2 min, probably fine hardcoded
- [ ] GCP Routes API for the Waterloo→work walk time (more variable, depends on which bus stop)
- [ ] `/journey` endpoint calls bus + train Lambdas, filters trains by catchability, returns full chain

Full door-to-door chain once complete:
`K2 from Parklands → Surbiton train → Waterloo → (tube or bus, weather-dependent) → work`

## Multi-user / configurability

Currently all stops, routes and locations are hardcoded for one commute. To make T3 useful for others:
- Needs a one-time setup screen (stops, routes, home location)
- Main screen stays zero-interaction once configured
- Tension: setup adds complexity, but only visited once
- Not a priority until the app is stable and worth sharing

