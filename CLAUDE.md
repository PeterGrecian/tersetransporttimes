# TerseTransportTimes (T3)

Android app showing bus and train times for a specific commute. Designed to be **minimalist and zero-interaction** — open it and the right information is already showing based on GPS location.

## Design Philosophy

- No configuration, no menus, no settings
- GPS determines what to show automatically (near home → buses home; near Surbiton → buses to Surbiton; near Waterloo → trains)
- Data refreshes every 30 seconds without user action
- Tap a bus time to arm an alarm; it sounds every 3 minutes as the bus approaches, then disarms automatically

## Architecture

```
Android app (Kotlin/Compose)
    ├── Bus times  → AWS Lambda: t3        → TfL Arrivals API
    └── Train times → AWS Lambda: t3-trains → National Rail Darwin API (SOAP)
```

Both Lambdas are behind API Gateway at:
```
https://vz66vhhtb9.execute-api.eu-west-1.amazonaws.com/
```

## Lambda Functions

### t3 (buses)
- **File:** `t3.py` | **Handler:** `t3.lambda_handler` | **Runtime:** Python 3.12
- **Endpoint:** `GET /t3?stop=parklands` or `?stop=surbiton`
- **Response:**
  ```json
  {"stop": "Parklands", "destination": "Surbiton", "seconds": [240, 480]}
  ```
- **Route:** K2 bus only
- **Max buses:** 2

### t3-trains
- **File:** `trains.py` | **Handler:** `trains.lambda_handler` | **Runtime:** Python 3.12
- **Endpoint:** `GET /trains?from=sur&to=wat` (or `from=wat&to=sur`)
- **Response:**
  ```json
  {
    "originName": "Surbiton", "destinationName": "London Waterloo",
    "departures": [{"scheduledDeparture": "1438", "eta": "1458",
                    "journeyMins": 20, "stops": 1, "delayMinutes": 0, "cancelled": false}]
  }
  ```

## Secrets (AWS SSM Parameter Store)

| Parameter | Used by |
|---|---|
| `/berrylands/tfl-api-key` | t3 Lambda (TfL bus API) |
| `/berrylands/darwin-api-key` | t3-trains Lambda (National Rail) |

## Key Locations

| Place | Lat | Lon | Radius |
|---|---|---|---|
| Parklands (home bus stop) | 51.39436 | -0.29321 | 400m |
| Surbiton Station | 51.39374 | -0.30411 | 400m |
| London Waterloo | 51.5031 | -0.1132 | 500m |

On-train detection: speed > 10 mph AND closer to the WAT–SUR rail line than to home.

## Build & Deploy (Linux)

### Prerequisites

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

`local.properties` must contain (literal path, not `$HOME`):
```
sdk.dir=/home/peter/Android/Sdk
```

### Deploy to phone

```bash
./deploy.sh        # build + adb install
```

Or manually:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `adb` says unauthorized: revoke USB authorizations on phone, reconnect, tap Allow.
If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: `adb uninstall com.example.tersetransporttimes` first.

### Deploy Lambdas

```bash
zip -j t3.zip t3.py && aws lambda update-function-code --function-name t3 --zip-file fileb://t3.zip
zip -j trains.zip trains.py && aws lambda update-function-code --function-name t3-trains --zip-file fileb://trains.zip
```

### Run tests

```bash
python3 -m pytest test_trains.py -v
```

## Bus Stop NaPTAN IDs

| Stop | NaPTAN ID | Direction |
|---|---|---|
| Parklands | 490010781S | Inbound (→ Surbiton) |
| Surbiton Station NK | 490015165B | Outbound (→ Hook/Home) |

See `NAPTAN_IDS.md` for full details.
