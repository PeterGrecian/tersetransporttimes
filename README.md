# TerseTransportTimes

Android app for displaying transport times.

## Build & Deploy (WSL2 to Physical Device)

### Prerequisites

Environment variables in `~/.bashrc`:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

### Quick Deploy

```bash
cd /home/tot/tersetransporttimes
./deploy.sh
```

### Manual Build & Deploy

```bash
# Build
./gradlew assembleDebug

# Check device is connected
/mnt/c/Users/*/AppData/Local/Android/Sdk/platform-tools/adb.exe devices

# Install
/mnt/c/Users/*/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

## Phone Setup

1. Enable Developer Options: Settings > About > tap "Build number" 7 times
2. Enable USB Debugging in Developer Options
3. Connect via USB, tap "Allow" when prompted to authorize computer

### ⚠️ Connection Issues from WSL2?
See **[USB_DEBUGGING.md](USB_DEBUGGING.md)** for detailed troubleshooting.

**Quick fix:** Revoke USB authorizations on phone, then from WSL run:
```bash
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe kill-server
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe start-server
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

## Debugging & Logs

### Check Lambda Logs

Quick check of CloudWatch logs:
```bash
./check-logs.sh t3-trains 30m    # Check trains Lambda, last 30 min
./check-logs.sh t3 1h             # Check bus Lambda, last hour
```

See [LOG_DEBUGGING.md](LOG_DEBUGGING.md) for full documentation.

### Current Known Issues

**Train data errors**: Huxley2 API is unreliable (free service).
See [TRAIN_API_OPTIONS.md](TRAIN_API_OPTIONS.md) for alternative APIs.

## Related Files

- `t3.py` - Bus times Lambda function
- `trains.py` - Train times Lambda function
- `terraform/` - Infrastructure as code (AWS resources)
- `check-logs.sh` - CloudWatch log viewer
- `deploy.sh` - Build and deploy to phone
- [LOG_DEBUGGING.md](LOG_DEBUGGING.md) - Debugging guide
- [TRAIN_API_OPTIONS.md](TRAIN_API_OPTIONS.md) - Alternative train APIs
