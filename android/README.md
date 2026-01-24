# Terse Transport Times - Android App

Android frontend for the K2 Bus Times serverless application.

## Features

- Display real-time K2 bus arrival times
- Auto-refresh every 30 seconds
- Pull-to-refresh gesture
- Loading indicators
- Error handling with retry
- Dark theme matching the web interface
- Clean, minimal design with monospace font

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM
- **Networking**: Retrofit + OkHttp
- **Async**: Kotlin Coroutines + StateFlow
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Setup Instructions

### 1. Deploy the Backend (if not already done)

First, ensure your AWS Lambda backend is deployed with JSON support:

```bash
cd ..
terraform -chdir=terraform init
terraform -chdir=terraform apply
```

Get your API Gateway endpoint URL from the Terraform output.

### 2. Configure the API Endpoint

Edit `app/src/main/java/com/tersetransporttimes/api/ApiConfig.kt`:

```kotlin
private const val BASE_URL = "https://your-api-gateway-url.execute-api.eu-west-1.amazonaws.com/"
```

Replace with your actual API Gateway URL from Terraform output.

### 3. Build the App

#### Option A: Android Studio (Recommended)

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `android` folder
4. Wait for Gradle sync to complete
5. Click "Run" or press Shift+F10

#### Option B: Command Line

```bash
cd android
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on Device

#### From Android Studio:
- Connect device via USB or use emulator
- Click Run button

#### From Command Line:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/tersetransporttimes/
│   │   │   ├── data/              # Data models
│   │   │   │   ├── BusArrival.kt
│   │   │   │   └── BusTimesResponse.kt
│   │   │   ├── api/               # Retrofit API
│   │   │   │   ├── BusTimesApi.kt
│   │   │   │   └── ApiConfig.kt
│   │   │   ├── repository/        # Data layer
│   │   │   │   └── BusTimesRepository.kt
│   │   │   ├── ui/                # UI layer
│   │   │   │   ├── BusTimesViewModel.kt
│   │   │   │   └── BusTimesScreen.kt
│   │   │   └── MainActivity.kt
│   │   ├── res/                   # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Architecture

The app follows MVVM (Model-View-ViewModel) architecture:

```
┌─────────────────────┐
│   BusTimesScreen    │ (View - Jetpack Compose)
│   - Displays UI     │
│   - Handles user    │
│     interactions    │
└──────────┬──────────┘
           │ observes StateFlow
           │
┌──────────▼──────────┐
│  BusTimesViewModel  │ (ViewModel)
│  - Manages UI state │
│  - Auto-refresh     │
│  - Business logic   │
└──────────┬──────────┘
           │ calls
           │
┌──────────▼──────────┐
│ BusTimesRepository  │ (Repository)
│  - API calls        │
│  - Caching          │
│  - Error handling   │
└──────────┬──────────┘
           │ uses
           │
┌──────────▼──────────┐
│    Retrofit API     │ (Network)
│  - HTTP requests    │
│  - JSON parsing     │
└─────────────────────┘
```

## Key Components

### ViewModel (BusTimesViewModel.kt)
- Manages UI state using StateFlow
- Implements auto-refresh every 30 seconds
- Handles loading, success, and error states

### Repository (BusTimesRepository.kt)
- Fetches data from API
- Caches last successful response
- Falls back to cache on network errors

### UI (BusTimesScreen.kt)
- Displays bus arrivals in a list
- Shows loading spinner during fetch
- Error screen with retry button
- Pull-to-refresh support

## Permissions

The app requires only one permission:
- `INTERNET` - To fetch bus times from the API

## Customization

### Change Refresh Interval

Edit `BusTimesViewModel.kt`:
```kotlin
private val AUTO_REFRESH_INTERVAL = 30_000L // milliseconds
```

### Change Theme Colors

Edit `BusTimesScreen.kt` to modify:
- Background: `Color(0xFF1a1a1a)`
- Accent: `Color(0xFF4a9eff)`
- Text: `Color.White`

## Troubleshooting

### Build Errors

If you encounter Gradle sync issues:
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

### Network Errors

1. Verify API endpoint is correct in `ApiConfig.kt`
2. Check that backend is deployed and accessible
3. Test endpoint with curl:
```bash
curl -H "Accept: application/json" https://your-api-url/
```

### App Crashes

Check Logcat in Android Studio for error messages. Common issues:
- Missing INTERNET permission
- Invalid API endpoint URL
- JSON parsing errors

## License

Same as parent project.
