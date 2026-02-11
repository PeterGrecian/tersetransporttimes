# ⚠️ USB DEBUGGING CONNECTION GUIDE ⚠️

## IMPORTANT: This process is needed EVERY time we connect to the phone from WSL2

### The Problem
WSL2 cannot directly access USB devices. We need to use the Windows ADB server, and the authorization flow can get stuck.

---

## STEP 1: Phone Side (User)

### Revoke USB Debugging Authorizations
**This is CRITICAL** - the phone needs to forget previous authorizations so it will prompt again:

1. Open **Settings** on phone
2. Go to **Developer Options**
3. Find **Revoke USB debugging authorizations**
4. Tap it to clear all previous authorizations
5. Plug in USB cable

---

## STEP 2: WSL Side (Claude)

### Kill and Restart ADB Server
The Windows ADB server must be restarted via PowerShell path from WSL:

```bash
# Kill existing ADB server
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe kill-server

# Start server and check devices
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe start-server
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

**Expected output:**
```
List of devices attached
28011JEGR16999	unauthorized    ← Device visible but not authorized yet
```

---

## STEP 3: Phone Side (User)

### Authorize the Connection
A popup should appear on the phone:

**"Allow USB debugging?"**
- ✓ Check **"Always allow from this computer"**
- Tap **OK** or **Allow**

---

## STEP 4: WSL Side (Claude)

### Verify Connection
```bash
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

**Expected output:**
```
List of devices attached
28011JEGR16999	device    ← Now shows as "device" instead of "unauthorized"
```

---

## Quick Reference Commands

### Check if device is connected:
```bash
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe devices
```

### Install APK:
```bash
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk
```

### View logs:
```bash
/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat MainActivity:D DistanceReadout:D *:S
```

---

## Why This Is Necessary

1. **WSL2 USB limitation**: WSL2 doesn't have direct USB access
2. **ADB server conflict**: Old server states can block new connections
3. **Authorization cache**: Android caches authorizations and may not prompt again without revocation

## Device Info
- **Device ID**: 28011JEGR16999
- **Windows ADB path**: `/mnt/c/Users/tot/AppData/Local/Android/Sdk/platform-tools/adb.exe`
