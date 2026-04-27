# Security Guide

> 中文版請見 [SECURITY_zh.md](SECURITY_zh.md)

This document describes security considerations for developing, building, and deploying Gsyn Java.

---

## API Key Management

### Google Maps API Key

The Maps API key is injected at **build time** via `local.properties` and `manifestPlaceholders` in `app/build.gradle`. It is **never** hardcoded in source files.

```properties
# local.properties  (NOT committed to version control)
MAPS_API_KEY=AIzaSy...yourkey...
```

`app/build.gradle` reads it:

```groovy
android {
    defaultConfig {
        manifestPlaceholders = [mapsApiKey: project.findProperty("MAPS_API_KEY") ?: ""]
    }
}
```

**Rules:**
- `local.properties` is listed in `.gitignore` — never commit it
- For CI/CD, store the key in GitHub Secrets as `MAPS_API_KEY` and pass it via `-P` Gradle property
- Restrict your API key in the Google Cloud Console to the `com.opensynaptic.gsynjava` package and your release SHA-1 fingerprint

### Restricting the API Key (Google Cloud Console)

1. Open [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials)
2. Click your Maps API key → **Edit**
3. Under **Application restrictions**, select **Android apps**
4. Add an entry:
   - Package name: `com.opensynaptic.gsynjava`
   - SHA-1 certificate fingerprint: *(your debug or release fingerprint)*
5. Under **API restrictions**, restrict to only the APIs this app uses

**Get your debug SHA-1:**
```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

**Get your release SHA-1:**
```bash
keytool -list -v \
  -keystore release.jks \
  -alias gsyn-release \
  -storepass YOUR_STORE_PASSWORD
```

---

## Keystore Management

### Generating a Release Keystore

```bash
keytool -genkeypair \
  -storetype JKS \
  -alias gsyn-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks
```

> ⚠️ **The keystore must be JKS format.** PKCS12 keystores will fail the CI validation step.

### What to Keep Secret

| File / Value | Where to store | Never do |
|-------------|---------------|----------|
| `release.jks` | Encrypted secret storage; base64-encode for CI | Commit to Git |
| Store password | GitHub Secret `STORE_PASSWORD` | Hardcode in `build.gradle` |
| Key password | GitHub Secret `KEY_PASSWORD` | Print to CI logs |
| `local.properties` | Local disk only | Commit to Git |
| `MAPS_API_KEY` | `local.properties` or CI Secret | Hardcode in source code |

### Keystore Backup

Keep **at least two encrypted backups** of your `release.jks` in separate locations. Losing the keystore means you can never publish an update to the same Play Store listing.

---

## local.properties — The Golden Rule

```
local.properties is in .gitignore — it must NEVER be committed.
```

This file may contain:
- `sdk.dir` — path to Android SDK (machine-specific, not sensitive)
- `MAPS_API_KEY` — sensitive
- Signing credentials for local release builds — sensitive

If you accidentally commit `local.properties`, rotate your API keys and passwords immediately.

---

## Network Security

### UDP

The Gsyn protocol uses **unencrypted UDP**. This is acceptable for:
- Local LAN deployments
- Lab/development environments
- Isolated IoT networks

It is **not** appropriate for:
- Sending sensitive sensor data over the public internet
- Production deployments without a VPN tunnel

### MQTT

The Paho client in this project connects without TLS. For production use, configure your MQTT broker with TLS and modify `TransportManager` to use an `MqttConnectOptions` with SSL socket factory.

---

## Data Stored on Device

Gsyn Java stores the following data in SQLite on-device:

| Table | Contents | Retention |
|-------|---------|-----------|
| `devices` | Device ID, label, last-seen timestamp, online status | Indefinite |
| `sensor_data` | Sensor readings with timestamps | 7 days (auto-purged) |
| `alerts` | Alert records | Indefinite (manual clear) |
| `rules` | Rule definitions | Indefinite |
| `operations_log` | Rule action log | Indefinite |

All data is stored in the app's private SQLite database at:
```
/data/data/com.opensynaptic.gsynjava/databases/gsyn.db
```

This is inaccessible to other apps without root access (standard Android sandbox).

---

## Reporting Security Issues

If you discover a security vulnerability, please **do not open a public GitHub Issue**. Instead, email the maintainer directly or use GitHub's private security advisory feature:

**GitHub → Security → Advisories → Report a vulnerability**

---

*For CI/CD security configuration, see [CI_CD.md](CI_CD.md).*

