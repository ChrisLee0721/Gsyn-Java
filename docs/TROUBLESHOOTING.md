# Troubleshooting Guide

> 中文版請見 [TROUBLESHOOTING_zh.md](TROUBLESHOOTING_zh.md)

This document covers the most common problems developers and users encounter with Gsyn Java and how to fix them.

---

## Table of Contents

1. [Google Maps shows a blank/white screen](#1-google-maps-shows-a-blankwhite-screen)
2. [Run button (▶) is greyed out in Android Studio](#2-run-button--is-greyed-out-in-android-studio)
3. [UDP transport receives no data](#3-udp-transport-receives-no-data)
4. [MQTT transport does not connect](#4-mqtt-transport-does-not-connect)
5. [CI/CD keystore errors](#5-cicd-keystore-errors)
6. [Language switching has no effect](#6-language-switching-has-no-effect)
7. [Theme colours do not update after changing preset](#7-theme-colours-do-not-update-after-changing-preset)
8. [Dashboard shows empty cards / zero values](#8-dashboard-shows-empty-cards--zero-values)
9. [App crashes on startup](#9-app-crashes-on-startup)
10. [Gradle sync fails](#10-gradle-sync-fails)

---

## 1. Google Maps shows a blank/white screen

**Symptom:** The Map page loads but shows only a white or grey tile with no map content.

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| `MAPS_API_KEY` not set | Add `MAPS_API_KEY=AIzaSy...` to `local.properties` |
| API key has wrong SHA-1 fingerprint | Run `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` and add the output SHA-1 to your Google Cloud Console credential |
| Required APIs not enabled | Enable **Maps SDK for Android** and **Maps SDK for Android** in Google Cloud Console → APIs & Services |
| Billing not enabled on the GCP project | Google Maps requires a billing account (free tier available) |
| `commitNow()` not used for map fragment | See `ARCHITECTURE.md §Google Maps Integration` — always use `commitNow()` |
| Application ID mismatch | The debug build uses `com.opensynaptic.gsynjava` — ensure your API key restriction matches exactly |

**Quick diagnosis:** Check Logcat for `MAPS_API_KEY` or `AuthFailure` tags.

---

## 2. Run button (▶) is greyed out in Android Studio

**Symptom:** The green triangle play button is not clickable.

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| Gradle sync not complete / failed | Wait for sync, or click **File → Sync Project with Gradle Files** |
| No run configuration selected | Click the dropdown next to ▶ and select `app` |
| No device / emulator connected | Start an AVD via **Device Manager**, or connect a physical device with USB debugging enabled |
| SDK not installed | Open **SDK Manager** and install Android SDK Platform 34 |
| Project not recognised as Android | Ensure you opened the root folder (containing `settings.gradle`), not a subfolder |

---

## 3. UDP transport receives no data

**Symptom:** Devices are sending packets but nothing appears in the Dashboard.

**Diagnosis steps:**

1. Check Settings → UDP is **enabled** and the port matches your sender (default `9876`)
2. On a physical device, confirm the device and sender are on the same network
3. On an emulator, UDP from the host machine needs port forwarding:
   ```bash
   adb forward udp:9876 udp:9876
   ```
4. Check that no firewall is blocking port 9876 (Windows Defender, iptables, etc.)
5. Enable Logcat filter `TransportManager` to see raw receive events
6. Send a test packet using the **Send** tab in the app and observe if the loopback works

**Android emulator note:** The emulator's virtual network isolates it from the host. Use `10.0.2.2` as the host address when sending from the host machine to the emulator.

---

## 4. MQTT transport does not connect

**Symptom:** MQTT toggle is on but status shows "Disconnected".

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| Broker hostname incorrect | Use an IP address instead of a hostname if DNS is not resolving |
| Port blocked | Default MQTT is `1883` (unencrypted). Check broker firewall rules |
| Broker requires authentication | Gsyn Java currently supports anonymous connections only |
| Topic format wrong | Wildcard topics must use `#` for multi-level (e.g. `opensynaptic/#`) |
| Broker TLS/SSL only | The Paho client is configured without TLS; connect to a non-TLS endpoint |

---

## 5. CI/CD keystore errors

**Symptom:** GitHub Actions fails with keystore-related errors such as:

```
Keystore type: (empty)
❌ Error: Keystore must be JKS format
```

or

```
error: alias not found
```

**Root cause:** The keystore stored in GitHub Secrets must be **JKS format**, not PKCS12.

**Generate a correct JKS keystore:**

```bash
keytool -genkeypair \
  -storetype JKS \
  -alias gsyn-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -keystore release.jks \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD \
  -dname "CN=Gsyn Release, OU=Dev, O=OpenSynaptic, L=Unknown, ST=Unknown, C=US"
```

**Convert existing PKCS12 to JKS:**

```bash
keytool -importkeystore \
  -srckeystore release.p12 \
  -srcstoretype PKCS12 \
  -destkeystore release.jks \
  -deststoretype JKS
```

**Encode and upload to GitHub Secrets:**

```bash
# macOS/Linux
base64 -i release.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

Required GitHub Secrets:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64-encoded `release.jks` |
| `KEY_ALIAS` | `gsyn-release` (or your alias) |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Store password |

---

## 6. Language switching has no effect

**Symptom:** Changing the language in Settings still shows the previous language.

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| Hardcoded strings in Java/XML | All user-visible text must use `getString(R.string.xxx)` or `@string/xxx` — never hardcoded |
| `values-zh/strings.xml` missing the key | Add the missing key to the Chinese strings file |
| Activity not recreating | `LocaleHelper.applyAndSave()` calls `AppCompatDelegate.setApplicationLocales()` which triggers recreation automatically — if not happening, check `LocaleHelper` |
| Android < 13 without `locale_config.xml` | On Android 13+, `res/xml/locale_config.xml` and `android:localeConfig` in the manifest are required |

---

## 7. Theme colours do not update after changing preset

**Symptom:** Selecting a new accent/background chip in Settings has no visible effect.

**Fix:** Theme changes require **Activity recreation**. The Settings "Save" button calls `requireActivity().recreate()`. If you are testing a code change that applies a theme overlay, ensure `AppThemeConfig.applyTheme(activity)` is called before `super.onCreate()` — it must be the very first call.

---

## 8. Dashboard shows empty cards / zero values

**Symptom:** Cards are visible but all values show `0`, `--`, or `N/A`.

**Causes & Fixes:**

| Cause | Fix |
|-------|-----|
| No transport enabled | Go to Settings → enable UDP or MQTT, then Save |
| No device has sent data yet | Send a test packet from the **Send** tab or from OpenSynaptic firmware |
| Database empty | This is expected on first launch — cards display `0` intentionally. Values appear after the first received packet |
| Card type hidden | Settings → toggle the card switch ON |

---

## 9. App crashes on startup

**Check Logcat first.** Common causes:

| Exception | Cause | Fix |
|-----------|-------|-----|
| `NullPointerException` in `AppController` | `AppController.get()` called before `Application.onCreate()` | Ensure `AppController` is only accessed from `Activity`/`Fragment`/`Service` contexts |
| `SQLiteException: no such table` | Database schema changed without migration | Uninstall the app and reinstall (development only), or add a migration in `AppDatabaseHelper` |
| `Resources$NotFoundException` | Missing string resource in one language | Add the key to both `values/strings.xml` and `values-zh/strings.xml` |
| `IllegalStateException: commitNow()` | Fragment transaction inside `commitNow()` called after `onSaveInstanceState` | Ensure map fragment is added in `onViewCreated`, not later |

---

## 10. Gradle sync fails

**Common errors:**

| Error | Fix |
|-------|-----|
| `Could not resolve com.google.android.gms:play-services-maps` | Check internet connection; if behind a proxy set proxy in `gradle.properties` |
| `SDK location not found` | Create `local.properties` with `sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk` |
| `Duplicate class kotlin.collections` | Add `configurations.all { resolutionStrategy { force ... } }` or update AGP |
| `minSdk(24) > device API` | Use an emulator or device running Android 7.0+ |
| Build cache corruption | Run `./gradlew clean` then retry |

---

*If your issue is not listed here, open a GitHub Issue with the full Logcat output and Gradle error message.*

