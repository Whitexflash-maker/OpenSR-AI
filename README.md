# FeatherX

**Lightweight. Intelligent. Powerful.**

FeatherX is an open-source Android app (Kotlin + Jetpack Compose) that is being built in small, tested steps for low-memory phones (first target: 4 GB RAM). It will grow into a gaming dashboard, performance profiles, a real device monitor.

**Status: v0.3.0-dev, early work in progress.** Only parts confirmed on a real phone are called "working". Everything else is either marked "built in CI, not yet confirmed" or "not built", and the app says so on screen instead of pretending.

## Status

| Part | State |
|---|---|
| App shell (dark theme, 5 tabs, edge-to-edge) | Confirmed on a phone |
| Game Space (library, add/remove, launch, recents) | Built in CI; not yet confirmed on a phone |
| Pro Tools sidebar (crosshair, filter, stopwatch, DND mute, stats + warning badge, per-game profiles) | Built in CI; not yet confirmed on a phone |
| Pro Tools screen magnifier | Not built (needs screen-capture permission) |
| Performance modes LOW / MEDIUM / HIGH / ULTRA / AUTO | Not built |
| Device monitor screen, thermal and memory protection | Not built (live stats exist only inside the sidebar) |

The AI image upscaler and ONNX Runtime were removed in v0.3.0 to keep the app small.

## Pro Tools sidebar
Needs "Display over other apps" (switched on in Android settings). Start it from the Pro Tools tab; a slim handle appears on the right edge. Tap to open, drag to move. Two mini-tabs:
- **Tools**: crosshair (Dot / Cross / Ring, colours, size, X/Y offset), screen filter (Lift / Warm / Dim + strength), stopwatch, Do-Not-Disturb mute (needs one-time DND access), per-game profile (crosshair + filter, saved for the game last launched from the Games tab).
- **Stats**: battery temperature (battery sensor, not CPU), free RAM, ping (TCP connect time to 1.1.1.1:443, not a game server), phone-wide speed. A colour badge shows when the battery is above 40 C or RAM is critically low. Measured only while the panel is open.

A screen filter can only be laid over the picture, so it cannot add real brightness; "Lift" lowers contrast so very dark areas are easier to see. Some online games ban crosshair overlays; use at your own risk. Frosted blur behind the panel is used only on Android 12+ devices that support it. Not built: screen magnifier.

## Principles
- Public Android APIs only. No changes to other apps or games, no clock-speed or thermal overrides, no FPS promises.
- Original code, original branding.
- A metric that cannot be measured is shown as unavailable, never invented.
- Measure performance instead of promising it.

## Build (GitHub Actions, no PC needed)
Actions tab, then **Build debug APK**, then **Run workflow**. When it finishes, open **Releases -> FeatherX latest debug build** and download `FeatherX-debug.apk` (a plain APK, no zip). The run page also keeps a `FeatherX-debug-apk` artifact (zip, 14 days). Android 9 (API 28) or newer is required.

Toolchain: JDK 17, Gradle 8.13 (installed by the workflow; the repo has no Gradle wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.2.20, compileSdk/targetSdk 35, minSdk 28, Compose BOM 2025.08.00.

## Tests
`gradle :app:testDebugUnitTest` runs the unit tests for the Game Space library logic, device-name formatting, overlay settings and speed/memory formatting. The overlay windows themselves have no automated test; they are checked by hand on a phone.

## Roadmap
v1.0 Game Space, Pro Tools sidebar, performance control, device monitoring. v1.5 advanced profiles and better image AI. v2.0 video upscaling. v3.0 temporal reconstruction research. v4.0 game-engine integration research (developer-supplied frames only, never injection into third-party games).

## Licence
Original code: Apache-2.0 (see `LICENSE`). Third-party components keep their own licences: AndroidX and Jetpack Compose (Apache-2.0).
