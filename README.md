# 🚌 Terse Transport Times (t3)

Minimal serverless app for real-time London bus arrivals. Backend on AWS Lambda, frontend on Android.

## Project Structure

```
tersetransporttimes/
├── t3.py                   # AWS Lambda function (Python)
├── terraform/              # Infrastructure as Code
├── android/                # Android app (Kotlin + Jetpack Compose)
├── mock_backend.py         # Local development server
├── DEPLOYMENT.md           # AWS deployment guide
└── MOCK_BACKEND.md         # Local development guide
```

## Quick Start Options

### Option 1: Local Development (No AWS needed)

Perfect for testing the Android app without deploying:

1. **Start mock backend:**
   ```bash
   python3 mock_backend.py
   ```

2. **Open Android app in Android Studio:**
   - Open the `android/` folder
   - App is already configured to use mock backend
   - Click Run

See **[MOCK_BACKEND.md](MOCK_BACKEND.md)** for details.

### Option 2: Full AWS Deployment

Deploy the real backend to AWS Lambda:

1. **Add GitHub Secrets** (one-time setup):
   - Go to repo Settings → Secrets → Actions
   - Add: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`

2. **Deploy automatically:**
   - Push to main branch (or manually trigger in Actions tab)
   - Get API URL from deployment summary

3. **Configure Android app:**
   ```kotlin
   // android/app/src/main/java/com/tersetransporttimes/api/ApiConfig.kt
   private const val USE_MOCK = false
   private const val PROD_URL = "https://your-api-url.amazonaws.com/"
   ```

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for details.

## Features

### Backend (AWS Lambda)
- 🚀 Serverless Python function
- 🔄 Fetches real-time TfL bus data
- 📊 Returns JSON, HTML, or plain text
- ⚡ Fast & minimal (128MB RAM, ~10s timeout)
- 🌍 Deployed in London region (eu-west-1)

### Android App
- 📱 Modern Jetpack Compose UI
- 🎨 Dark theme, monospace font
- 🔄 Auto-refresh every 30 seconds
- 👆 Pull-to-refresh gesture
- ⚠️ Error handling with retry
- 🏗️ MVVM architecture
- 📦 Min SDK 24 (Android 7.0+)

### Mock Backend
- 🧪 Local Python server
- 🎲 Realistic random data
- 🚀 Zero cloud dependencies
- 💻 Perfect for development

## API Endpoints

The backend supports three response formats via `Accept` header:

```bash
# JSON (for Android app)
curl -H "Accept: application/json" https://your-api-url/

# HTML (for browsers)
curl https://your-api-url/

# Plain text
curl -H "Accept: text/plain" https://your-api-url/
```

### JSON Response Format
```json
{
  "timestamp": "14:35",
  "route": "K2",
  "arrivals": [
    {
      "stop": "Bethnal Green, Wellington Row",
      "direction": "inbound",
      "minutes": 3
    },
    ...
  ]
}
```

## Technology Stack

### Backend
- **Runtime:** Python 3.12
- **Platform:** AWS Lambda + API Gateway
- **IaC:** Terraform
- **API:** Transport for London (TfL)

### Android
- **Language:** Kotlin
- **UI:** Jetpack Compose + Material Design 3
- **Networking:** Retrofit + OkHttp
- **Architecture:** MVVM with StateFlow
- **Build:** Gradle 8.2

### CI/CD
- **Automation:** GitHub Actions
- **Secrets:** GitHub Secrets (AWS credentials)
- **Triggers:** Push to main/claude/** branches

## Development Workflow

### Local Development
```bash
# Terminal 1: Run mock backend
python3 mock_backend.py

# Terminal 2: Run Android app
cd android
./gradlew installDebug
# Or use Android Studio
```

### Deploy to AWS
```bash
# Automatic (recommended)
git push origin main

# Manual with AWS CLI
./update

# Manual with Terraform
cd terraform
terraform apply
```

## File Guide

| File | Purpose |
|------|---------|
| `t3.py` | Lambda function - fetches bus data, formats responses |
| `mock_backend.py` | Local development server with mock data |
| `update` | Bash script to quickly deploy Lambda code changes |
| `terraform/` | AWS infrastructure definition |
| `android/` | Complete Android app project |
| `DEPLOYMENT.md` | AWS deployment instructions |
| `MOCK_BACKEND.md` | Local development instructions |
| `.github/workflows/` | GitHub Actions CI/CD |

## Customization

### Change Bus Route

Edit `t3.py`:
```python
ROUTE = "K2"  # Change to any London bus route
```

### Change Refresh Interval

Edit Android `BusTimesViewModel.kt`:
```kotlin
private val AUTO_REFRESH_INTERVAL = 30_000L  // milliseconds
```

### Change Theme Colors

Edit Android `BusTimesScreen.kt`:
```kotlin
Color(0xFF1a1a1a)  // Background
Color(0xFF4a9eff)  // Accent blue
```

## Troubleshooting

### Android app shows "Unable to load bus times"

**Using Mock Backend:**
- Ensure `python3 mock_backend.py` is running
- Check `USE_MOCK = true` in `ApiConfig.kt`
- Emulator: Use `http://10.0.2.2:8000/`
- Real device: Use your computer's IP

**Using AWS Backend:**
- Verify deployment succeeded (check Actions tab)
- Confirm API URL in `ApiConfig.kt` is correct
- Test endpoint: `curl -H "Accept: application/json" <your-url>`

### GitHub Actions deployment fails

- Check secrets are set correctly in repo settings
- Ensure Lambda function exists (run `terraform apply` first)
- View logs in Actions tab for details

See respective guide files for more troubleshooting.

## Contributing

This is a personal project, but suggestions welcome via issues!

## License

MIT License - Feel free to adapt for your own use.

---

**Current Status:** K2 route monitoring | London (eu-west-1) | Serverless Architecture

Built with ☕ for minimal, essential information.
