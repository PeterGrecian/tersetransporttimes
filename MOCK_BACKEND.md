# 🚌 Mock Backend for Local Development

Test the Android app without deploying to AWS! This mock backend simulates the Lambda API locally.

## Quick Start

### 1. Start the Mock Server

On your computer (not phone):

```bash
python3 mock_backend.py
```

You should see:
```
╔════════════════════════════════════════════════════════════╗
║  🚌 Mock K2 Bus Times Server Running                       ║
╠════════════════════════════════════════════════════════════╣
║  Local:   http://localhost:8000                            ║
║  Network: http://<your-ip>:8000                            ║
║                                                            ║
║  Test it: curl http://localhost:8000                       ║
║                                                            ║
║  Press Ctrl+C to stop                                      ║
╚════════════════════════════════════════════════════════════╝
```

### 2. Test It Works

```bash
curl http://localhost:8000
```

You should see JSON like:
```json
{
  "timestamp": "14:35",
  "route": "K2",
  "arrivals": [
    {"stop": "Bethnal Green, Wellington Row", "direction": "inbound", "minutes": 3},
    {"stop": "Shoreditch High Street", "direction": "inbound", "minutes": 7},
    ...
  ]
}
```

### 3. Configure Android App

The app is already configured to use the mock backend by default.

**Edit:** `android/app/src/main/java/com/tersetransporttimes/api/ApiConfig.kt`

```kotlin
private const val USE_MOCK = true  // Already set!
```

### 4. Run the Android App

#### Using Android Emulator (Easiest)
- The app uses `http://10.0.2.2:8000/` automatically
- `10.0.2.2` is a special IP that Android emulator uses to access your host machine
- Just run the app in Android Studio

#### Using Real Android Device
1. Connect your phone and computer to the **same WiFi network**
2. Find your computer's IP address:
   ```bash
   # On Linux/Mac
   ifconfig | grep "inet " | grep -v 127.0.0.1

   # On Windows
   ipconfig | findstr IPv4
   ```
   You'll see something like `192.168.1.100`

3. Update `ApiConfig.kt`:
   ```kotlin
   private const val MOCK_URL = "http://192.168.1.100:8000/"  // Use your IP!
   ```

4. Rebuild and run the app

## What the Mock Backend Does

- ✅ Returns realistic K2 bus arrival data
- ✅ Random arrival times (2-18 minutes)
- ✅ Multiple stops in both directions (inbound/outbound)
- ✅ Current timestamp
- ✅ Same JSON format as the real AWS backend
- ✅ CORS enabled for web testing
- ✅ Logs each request with timestamp

## Mock Data

The mock backend returns 7 bus stops with random arrival times:
- Bethnal Green, Wellington Row (inbound)
- Shoreditch High Street (inbound)
- Liverpool Street Station (inbound)
- Aldgate (inbound)
- Bank (outbound)
- Old Street Station (outbound)
- Essex Road (outbound)

Times are randomized on each request to simulate real-time updates.

## Switching to Production

When you're ready to use the real AWS backend:

1. Deploy the backend (see `DEPLOYMENT.md`)
2. Get your API Gateway URL from GitHub Actions
3. Update `ApiConfig.kt`:
   ```kotlin
   private const val USE_MOCK = false
   private const val PROD_URL = "https://your-real-api-url.amazonaws.com/"
   ```
4. Rebuild the app

## Custom Port

Run on a different port:
```bash
python3 mock_backend.py 9000
```

Then update `ApiConfig.kt`:
```kotlin
private const val MOCK_URL = "http://10.0.2.2:9000/"
```

## Troubleshooting

### "Connection refused" on emulator
- Make sure mock server is running
- Verify you're using `10.0.2.2` not `localhost`
- Check Android emulator can access network

### "Connection refused" on real device
- Ensure phone and computer are on same WiFi
- Verify the IP address is correct
- Check firewall isn't blocking port 8000
- Try running: `python3 -m http.server 8000` to test basic connectivity

### "Network security error" on Android 9+
Android 9+ blocks cleartext (HTTP) traffic by default. The app's `AndroidManifest.xml` needs:
```xml
<application
    android:usesCleartextTraffic="true"
    ...>
```

This is already configured in the app for development.

## Testing the App Features

With the mock backend running, you can test:
- ✅ Initial loading state
- ✅ Bus times list display
- ✅ Pull-to-refresh (gets new random times)
- ✅ Auto-refresh every 30 seconds (watch times change)
- ✅ Error handling (stop the server and see error screen)
- ✅ Retry button (restart server, tap retry)

## Production Ready

The mock backend is perfect for:
- 🎨 UI development
- 🧪 Testing app features
- 🔄 Testing refresh logic
- 📱 Demos without internet
- 🚀 Development before AWS deployment

Enjoy building! 🎉
