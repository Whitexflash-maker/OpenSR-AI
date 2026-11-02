# FeatherX

**Lightweight. Intelligent. Powerful.**

FeatherX is an open-source Android app (Kotlin + Jetpack Compose) that is being built in small, tested steps for low-memory phones (first target: 4 GB RAM). It will grow into a gaming dashboard, performance profiles, a real device monitor and on-device AI image upscaling.

**Status: v0.2.0-dev, early work in progress.** Only the parts marked "working" below have been built and run on a real phone. Everything else is not built yet, and the app says so on screen instead of pretending.

## Status

| Part | State |
|---|---|
| App shell (dark theme, 5 tabs, edge-to-edge) | Built in CI; not yet confirmed on a phone |
| AI Image Upscaler (x3, ONNX Runtime, CPU, tiled) | Confirmed on a phone before the shell refactor (model loaded, 640x640 to 1920x1920 in 2.86 s, one run). Re-check needed after the refactor |
| Game Space | Not built (Step 5) |
| Performance modes LOW / MEDIUM / HIGH / ULTRA / AUTO | Not built (Step 6) |
| Device monitor | Not built (Step 7) |
| Thermal and memory protection | Not built (Step 8) |
| Image export, before/after comparison slider | Not built (Step 9) |

## Principles
- Public Android APIs only. No changes to other apps or games, no clock-speed or thermal overrides, no FPS promises.
- Original code, original branding.
- A metric that cannot be measured is shown as unavailable, never invented.
- Measure performance instead of promising it.

## AI model

| | |
|---|---|
| File | `app/src/main/assets/super-resolution-10.onnx` (240,078 bytes) |
| Source | ONNX Model Zoo, `vision/super_resolution/sub_pixel_cnn_2016` (Shi et al., 2016, arXiv:1609.05158) |
| Licence | Apache-2.0 |
| SHA-256 | `85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e` |
| Input | `input`, float32, `[batch, 1, 224, 224]` (Y channel of YCbCr, scaled 0..1) |
| Output | `output`, float32, `[batch, 1, 672, 672]` (x3) |

How it is used: the image (at most 640 px on the long side) is cut into 208x208 tiles with 8 px context, the model runs on the luminance channel of each tile, and chroma comes from a bilinear x3 resize. The model is small (about 60k weights) so quality is modest, and only luminance is enhanced.

The SHA-256 is checked three ways: when the app starts (shown on the Upscaler tab), by the unit test `ModelAssetTest`, and by the CI workflow before building.

## Build (GitHub Actions, no PC needed)
Actions tab, then **Build debug APK**, then **Run workflow**. When it finishes, download the artifact `FeatherX-debug-apk` from the run page (a zip that contains `app-debug.apk`) and install it. Android 9 (API 28) or newer is required.

Toolchain: JDK 17, Gradle 8.13 (installed by the workflow; the repo has no Gradle wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.2.20, compileSdk/targetSdk 35, minSdk 28, Compose BOM 2025.08.00, ONNX Runtime Android 1.27.0.

## Tests
`gradle :app:testDebugUnitTest` runs the tile-planner, colour-conversion and model-file tests. ONNX inference itself has no automated test; it is checked by hand on a device.

## Roadmap
v1.0 Game Space, performance control, device monitoring, basic AI image upscaling. v1.5 advanced profiles and better image AI. v2.0 video upscaling. v3.0 temporal reconstruction research. v4.0 game-engine integration research (developer-supplied frames only, never injection into third-party games).

## Licence
Original code: Apache-2.0 (see `LICENSE`). Third-party components keep their own licences: ONNX Runtime (MIT), the model above (Apache-2.0), AndroidX and Jetpack Compose (Apache-2.0).
