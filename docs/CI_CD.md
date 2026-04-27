# CI/CD Guide

> 中文版請見 [CI_CD_zh.md](CI_CD_zh.md)

This document explains the GitHub Actions CI/CD pipeline for Gsyn Java — how it works, how to configure it, and how to debug failures.

---

## Overview

The pipeline runs on every push to `main` and on every pull request. It also runs when a version tag (e.g. `v1.3.0`) is pushed, in which case it additionally builds a **signed release APK** and creates a GitHub Release.

```
Push to main / PR
    └── build-and-test job
          ├── Checkout
          ├── Set up JDK 17
          ├── Gradle cache restore
          ├── ./gradlew assembleDebug
          └── ./gradlew test

Push tag vX.Y.Z
    └── build-and-test job  (above)
    └── release job (depends on build-and-test)
          ├── Decode keystore from KEYSTORE_BASE64 secret
          ├── Validate keystore format (JKS) and alias
          ├── ./gradlew assembleRelease -Pandroid.injected.signing.*
          └── Upload APK to GitHub Release
```

---

## Required GitHub Secrets

Go to **Repository → Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret Name | Description |
|-------------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded `release.jks` (JKS format) |
| `KEY_ALIAS` | Key alias inside the keystore (e.g. `gsyn-release`) |
| `KEY_PASSWORD` | Password for the private key |
| `STORE_PASSWORD` | Password for the keystore store |
| `MAPS_API_KEY` | Google Maps API key (injected at build time) |

### Encoding the keystore for the secret

```bash
# macOS/Linux
base64 -i release.jks | tr -d '\n' | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Clipboard
```

Paste the clipboard content as the value of `KEYSTORE_BASE64`.

---

## Workflow File Location

```
.github/workflows/android.yml
```

Key sections:

```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 --decode > /home/runner/work/_temp/release.jks

- name: Validate keystore
  run: |
    KEYSTORE_TYPE=$(keytool -J-Duser.language=en -list -v \
      -keystore /home/runner/work/_temp/release.jks \
      -storepass "${{ secrets.STORE_PASSWORD }}" 2>&1 \
      | grep "Keystore type:" | awk '{print $3}')
    if [ "$KEYSTORE_TYPE" != "JKS" ]; then
      echo "❌ Error: Keystore must be JKS format, got: $KEYSTORE_TYPE"
      exit 1
    fi

- name: Build release APK
  run: |
    ./gradlew assembleRelease \
      -Pandroid.injected.signing.store.file=/home/runner/work/_temp/release.jks \
      -Pandroid.injected.signing.store.password=${{ secrets.STORE_PASSWORD }} \
      -Pandroid.injected.signing.key.alias=${{ secrets.KEY_ALIAS }} \
      -Pandroid.injected.signing.key.password=${{ secrets.KEY_PASSWORD }}
```

---

## Triggering a Release

1. Update `versionCode` and `versionName` in `app/build.gradle`
2. Commit and push:
   ```bash
   git add app/build.gradle
   git commit -m "chore: bump version to 1.3.0"
   git push origin main
   ```
3. Tag and push:
   ```bash
   git tag -a v1.3.0 -m "v1.3.0: describe what changed"
   git push origin v1.3.0
   ```
4. GitHub Actions detects the tag, runs the full pipeline, and creates a Release with the APK attached.

---

## Debugging CI Failures

### Keystore errors

| Error message | Cause | Fix |
|--------------|-------|-----|
| `Keystore type: (empty)` | Wrong store password | Verify `STORE_PASSWORD` secret matches the keystore |
| `Keystore must be JKS format` | Keystore is PKCS12 | Re-generate using `-storetype JKS` |
| `alias not found` | `KEY_ALIAS` secret is wrong | Run `keytool -list -keystore release.jks` to list available aliases |
| `base64: invalid input` | `KEYSTORE_BASE64` value is corrupt | Re-encode the keystore file (no line breaks in the base64 string) |

### Build failures

- **`SDK not found`** — The CI runner uses `ANDROID_HOME` automatically. If this fails, check the `actions/setup-java` and `actions/setup-android` step versions.
- **`MAPS_API_KEY not set`** — Add the `MAPS_API_KEY` GitHub Secret.
- **`Gradle daemon failed to start`** — Add `-Dorg.gradle.daemon=false` to the Gradle command in CI.

### Viewing logs

Every job step writes to GitHub Actions logs. Click the failed step to expand the full output. For Gradle failures, look for `BUILD FAILED` and the `> Task :app:xxx FAILED` line that precedes it.

---

## Gradle Cache

The workflow caches the Gradle wrapper and dependencies using `actions/cache` keyed on the hash of `gradle/wrapper/gradle-wrapper.properties` and `app/build.gradle`. This typically reduces CI time from ~5 minutes to ~2 minutes after the first run.

If the cache causes issues (e.g. after a major dependency upgrade), manually invalidate it:

**Actions → Caches → delete the relevant cache entry**

---

## Environment Variables Summary

| Variable | Set by | Used in |
|----------|--------|---------|
| `JAVA_HOME` | `actions/setup-java` | Gradle |
| `ANDROID_HOME` | Runner image | Gradle |
| `MAPS_API_KEY` | GitHub Secret | `manifestPlaceholders` |
| Signing params | GitHub Secrets | `-P` Gradle properties |

---

*For keystore security details, see [SECURITY.md](SECURITY.md).  
For the release process checklist, see [CONTRIBUTING.md § Release Process](CONTRIBUTING.md#release-process).*

