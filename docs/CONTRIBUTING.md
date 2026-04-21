# Contributing Guide

> 中文版請見 [CONTRIBUTING_zh.md](CONTRIBUTING_zh.md)


This guide is for developers who want to add features, fix bugs, or submit improvements to Gsyn Java.

---

## Development Philosophy

> **Keep it readable over clever.**

This project is also an educational reference. Prefer an explicit 10-line method over a clever 2-line lambda that requires careful thought to understand. Junior developers and students should be able to follow the logic without a debugger.

---

## Branch & Commit Convention

```
main            → stable, tagged releases
feature/xxx     → new feature branch
fix/xxx         → bug fix branch
```

**Commit message format:**

```
type: short description (under 72 chars)

- bullet point detail
- another detail
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

---

## Before You Code — Checklist

- [ ] Read the relevant section of [ARCHITECTURE.md](ARCHITECTURE.md)
- [ ] Read [UI_PATTERNS.md](UI_PATTERNS.md) so your code follows existing patterns
- [ ] Run `./gradlew assembleDebug` to confirm the project compiles on your machine
- [ ] Check if there are existing helper methods in `UiFormatters`, `AppRepository`, or `AppThemeConfig` before writing new ones

---

## Adding a New Page / Fragment

1. Create `MyNewFragment.java` extending `Fragment`
2. Create `fragment_my_new.xml` layout
3. All colours must use `?attr/colorXxx` theme attributes — no hardcoded hex
4. All strings must be in `res/values/strings.xml` (EN) and `res/values-zh/strings.xml` (ZH)
5. Add navigation in `MainActivity`:
   - Bottom nav tab → add item to `res/menu/bottom_nav.xml` + handle in `setOnItemSelectedListener`
   - Drawer extension → add item to `res/menu/drawer_nav.xml` + handle in `onNavigationItemSelected`

---

## Adding a New Transport Protocol

1. Create `MyTransport.java` in `transport/`
2. Implement `start()`, `stop()`, `send(byte[])`
3. On each received packet, call:
   ```java
   DecodeResult result = PacketDecoder.decode(rawBytes);
   if (result.valid) {
       List<SensorReading> readings = DiffEngine.process(result.cmd, result.aid, result.rawBody);
       repository.insertSensorDataBatch(result.aid, readings);
       rulesEngine.evaluate(result.aid, readings);
       notifyMessageListeners(new DeviceMessage(result.aid, readings));
   }
   ```
4. Register start/stop in `TransportManager` and expose a toggle in `SettingsFragment`

---

## Adding a New Rule Action Type

1. Add a new string constant in `RulesEngine` (e.g. `"send_webhook"`)
2. Add a `case` branch in `RulesEngine.evaluate()` with the action implementation
3. Add the option to the rule creation dialog in `RulesMirrorFragment`
4. Add the string resource for the new action type in both language files

---

## String Resources Checklist

When adding any user-visible text:

```
res/values/strings.xml      → English (mandatory)
res/values-zh/strings.xml   → Chinese (mandatory)
```

Name format: `section_description`, e.g.:
- `dashboard_label_total_devices`
- `settings_single_device_mode`
- `mirror_rules_dialog_title`

Never use the same string key for unrelated concepts. If a phrase appears in two places, it is acceptable to have two keys (they might diverge in translation).

---

## Code Style

- **Java 8** — lambdas and streams are fine; records and `var` are not (minSdk 24 requires desugaring)
- **4-space indentation**
- **No wildcard imports** (`import java.util.*` is forbidden)
- **Null safety** — always null-check `binding` before view access in Fragment methods that can be called async
- **Background thread guard** — any callback from `TransportManager` is on a background thread; always `runOnUiThread()` before touching views

---

## Testing

Unit tests live in `app/src/test/`. The protocol layer is fully testable without Android:

```java
// Example: test packet decode
@Test
public void testDecodeValidPacket() {
    byte[] packet = buildTestPacket(CMD_DATA_FULL, 42, ...);
    DecodeResult result = PacketDecoder.decode(packet);
    assertTrue(result.valid);
    assertEquals(42, result.aid);
}
```

Instrumented tests (requiring a device/emulator) go in `app/src/androidTest/`.

Run all unit tests:
```bash
./gradlew test
```

---

## Release Process

1. Update `versionCode` and `versionName` in `app/build.gradle`
2. `git add -A && git commit -m "chore: bump version to x.y.z"`
3. `git tag -a vx.y.z -m "vx.y.z: Release notes here"`
4. `git push origin main --tags`
5. GitHub Actions builds the signed APK automatically (see `.github/workflows/`)

---

## Common Pitfalls

| Mistake | Correct Approach |
|---------|-----------------|
| `binding.tvFoo.setText(R.string.bar)` | `binding.tvFoo.setText(getString(R.string.bar))` — R.string is an int, not a String |
| Hardcoded `"5 分钟前"` in Java | `DateUtils.getRelativeTimeSpanString(...)` |
| `android:textColor="#000000"` in XML | `android:textColor="?attr/colorOnSurface"` |
| Querying DB in `onBindViewHolder` | Query in `Fragment.refresh()`, pass data via `Snapshot` |
| `commit()` for MapFragment | Must use `commitNow()` — see ARCHITECTURE.md §Google Maps |
| `getActivity().runOnUiThread(...)` without null check | `if (getActivity() != null) getActivity().runOnUiThread(...)` |
