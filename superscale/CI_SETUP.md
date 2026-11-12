# SuperScale — বিল্ড সেটআপ (একবারের কাজ)

সোর্স কোড এই ব্রাঞ্চের `superscale/` ফোল্ডারে আছে। APK বিল্ড করতে শুধু একটা
ওয়ার্কফ্লো ফাইল যোগ করতে হবে — GitHub App-এর Workflows permission না থাকায়
এটা স্বয়ংক্রিয়ভাবে করা যায়নি।

## ধাপ

1. নিচের লিংকে যান (নতুন ফাইল তৈরি হবে `superscale` ব্রাঞ্চে):

   https://github.com/Whitexflash-maker/OpenSR-AI/new/superscale

2. **File name** ঘরে লিখুন:

   ```
   .github/workflows/build-superscale.yml
   ```

   > `.github/workflows/` অংশটা হুবহু লিখতে হবে, নাহলে Actions ফাইলটা দেখবে না।

3. নিচের পুরো YAML কপি করে এডিটরে পেস্ট করুন (উপরের ডিফল্ট কনটেন্ট মুছে দিয়ে)।
4. নিচে **Commit changes** চাপুন, ব্রাঞ্চ হিসেবে **`superscale`** সিলেক্ট করা আছে
   কি না নিশ্চিত করুন।
5. কমিট হওয়া মাত্র বিল্ড নিজে থেকেই শুরু হবে (Actions ট্যাব → "Build SuperScale APK")।
   ~৪-৬ মিনিট সময় লাগবে।

## ওয়ার্কফ্লো YAML

```yaml
name: Build SuperScale APK

on:
  workflow_dispatch:
  push:
    branches: [ superscale ]
    paths:
      - 'superscale/**'
      - '.github/workflows/build-superscale.yml'

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: superscale
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install Android SDK packages
        run: |
          yes | sdkmanager --licenses > /dev/null 2>&1 || true
          sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.5'

      - name: Build debug APK
        run: gradle assembleDebug --no-daemon --stacktrace

      - name: Upload APK artifact
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: superscale-debug-apk
          path: superscale/app/build/outputs/apk/debug/*.apk

      - name: Publish release with APK
        if: success()
        uses: softprops/action-gh-release@v2
        with:
          tag_name: superscale-v1.0.${{ github.run_number }}
          name: SuperScale v1.0.${{ github.run_number }}
          body: Auto-built debug APK (edge-directed upscaler + RCAS).
          files: superscale/app/build/outputs/apk/debug/*.apk
```

## বিল্ড শেষে

- APK: Actions রান → নিচে **Artifacts** → `superscale-debug-apk`
- অথবা Releases পেজে `superscale-v1.0.N` ট্যাগে সরাসরি APK

## ইনস্টল

```
adb install -r app-debug.apk
```
অথবা APK ফোনে কপি করে ট্যাপ করুন ("Unknown sources" অন করতে হবে)।
