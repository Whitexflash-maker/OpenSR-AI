# OpenSR AI

Minimal Android app that upscales an image **x3** with a real pretrained ONNX super-resolution model, fully on-device (CPU, ONNX Runtime). Kotlin + Jetpack Compose.

**Status: not yet built or run.** No successful CI build and no on-device test has happened yet. Do not treat any behaviour below as confirmed until the Actions build is green and the APK has been tested on a phone.

## Model

| | |
|---|---|
| File | `app/src/main/assets/super-resolution-10.onnx` (240,078 bytes) |
| Source | ONNX Model Zoo, `vision/super_resolution/sub_pixel_cnn_2016` (Shi et al. 2016, arXiv:1609.05158) |
| License | Apache-2.0 |
| SHA-256 | `85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e` |
| Input | `input`, float32, `[batch, 1, 224, 224]` (Y channel of YCbCr, scaled 0..1) |
| Output | `output`, float32, `[batch, 1, 672, 672]` (x3) |
| Opset | 10 (Conv, Relu, Reshape, Transpose) |

Download (the exact copy whose hash was checked):
`https://media.githubusercontent.com/media/onnx/models/main/validated/vision/super_resolution/sub_pixel_cnn_2016/model/super-resolution-10.onnx`

### Verifying the hash
- The app shows "SHA-256: matches expected" on its model card at start-up.
- The GitHub Actions workflow checks size and SHA-256 before building.
- The unit test `ModelAssetTest` checks it again.

## How it works
1. Image is decoded and limited to 640 px on the long side (output at most 1920x1920) to be safe on 4 GB phones.
2. The image is cut into 208x208 core tiles with 8 px context (window 224x224, edges replicated).
3. The real ONNX model runs on the Y channel of every tile. Only the core of each output tile is kept.
4. Chroma (Cb/Cr) comes from a bilinear x3 resize of the original; it is combined with the model's Y.
5. The app shows total time and model-inference time.

## Limitations
- This is a very small 2016 model (about 60k weights). Quality is modest compared with modern models.
- Only the luminance channel is enhanced by the model.
- Requires Android 9 (API 28) or newer.

## Build (GitHub Actions, no PC)
Actions tab -> **Build debug APK** -> **Run workflow**. When it finishes, open the run and download the artifact `OpenSR-AI-debug-apk` (a zip that contains `app-debug.apk`).

Toolchain: JDK 17, Gradle 8.13 (installed by the workflow; the repo has no Gradle wrapper), Android Gradle Plugin 8.13.2, Kotlin 2.2.20, compileSdk/targetSdk 35, minSdk 28, Compose BOM 2025.08.00, ONNX Runtime Android 1.27.0.

## Tests
`gradle :app:testDebugUnitTest` runs tile-planner, colour-conversion and model-asset tests. The ONNX inference itself is not covered by automated tests.
