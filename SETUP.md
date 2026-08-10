# Narratrace Android — local toolchain setup

One-time setup so every phase of implementation can be compiled and verified before it lands.

Target: `./gradlew :app:assembleDebug` succeeds and produces an APK.

---

## What the project requires

Declared in the repo (do not change these to make a build pass — tell me instead):

| Component | Required version | Where it's declared |
|---|---|---|
| JDK | **17** | `app/build.gradle.kts` → `sourceCompatibility`, `kotlinOptions.jvmTarget` |
| Gradle | **8.13** | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.2 | `build.gradle.kts` |
| Kotlin | 2.2.20 | `build.gradle.kts` |
| compileSdk / targetSdk | **36** (Android 16) | `app/build.gradle.kts` |
| minSdk | 26 (Android 8.0) | `app/build.gradle.kts` |
| Build Tools | 36.0.0 | implied by compileSdk 36 |

Gradle itself is already vendored via the wrapper — you do **not** install Gradle separately.

---

## Step 1 — Install JDK 17

Check what you have:

```bash
java -version
/usr/libexec/java_home -V
```

If 17 is not listed, install it:

```bash
brew install --cask temurin@17
```

No Homebrew? Download the macOS `.pkg` for your chip (Apple Silicon = aarch64, Intel = x64):
https://adoptium.net/temurin/releases/?version=17

Then point your shell at it:

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
java -version    # must print 17.x
```

> JDK 21 also works with AGP 8.13, but stick to 17 — it's what the build files declare, and matching removes a whole class of "works on my machine" ambiguity.

---

## Step 2 — Install the Android SDK

**Recommended: Android Studio.** It manages SDK packages, gives you Logcat for debugging protected-media and auth failures, and provides an emulator. Worth it even if I do the code.

Download (pick the build matching your chip):
https://developer.android.com/studio

Install, launch, and in the setup wizard choose **Custom**, then make sure these are checked:

- Android SDK Platform **36**
- Android SDK Build-Tools **36.0.0**
- Android SDK Platform-Tools
- Android SDK Command-line Tools (latest)
- Android Emulator

If Studio is already installed, add them via **Settings → Languages & Frameworks → Android SDK → SDK Platforms / SDK Tools** (tick *Show Package Details* to select exact versions).

### Alternative: command-line only, no Android Studio

```bash
brew install --cask android-commandlinetools
echo 'export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools' >> ~/.zshrc
source ~/.zshrc
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```

*(On Intel Macs the Homebrew prefix is `/usr/local` instead of `/opt/homebrew`.)*

---

## Step 3 — Accept SDK licenses

Gradle will refuse to build without these.

```bash
# Android Studio install (default SDK location):
~/Library/Android/sdk/cmdline-tools/latest/bin/sdkmanager --licenses
```

Press `y` at each prompt.

---

## Step 4 — Point the project at your SDK

`local.properties` is gitignored (correctly — it's machine-specific).

```bash
cd ~/Claude/Projects/Raaj/narratrace-mobile-android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

If you used the command-line-tools route instead, set `sdk.dir` to your `ANDROID_HOME` path.

---

## Step 5 — First build

```bash
cd ~/Claude/Projects/Raaj/narratrace-mobile-android
./gradlew --version          # confirms wrapper downloads Gradle 8.13 and sees JDK 17
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

**Send me the full output either way — success or failure.** Two of the pinned versions
(AGP `8.13.2`, Compose BOM `2026.06.00`) are newer than anything I can verify offline, so
this first run is the real check on whether the scaffold's toolchain declarations are valid.

Expected artifact on success: `app/build/outputs/apk/debug/app-debug.apk`

---

## Step 6 — A device to run on

Either works; the emulator is enough for Phases 1–2, but **capture, camera, protected media,
and background upload work in Phase 3 must be validated on a physical device.**

**Emulator:** Android Studio → Device Manager → Create Device → Pixel 8 → system image API 36.

**Physical device:** enable Developer Options (tap Build Number 7×), enable USB Debugging, connect, then:

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

---

## Known issues I will fix on the first code pass

These are pre-existing defects in the scaffold, not setup problems:

1. **`app/proguard-rules.pro` is missing** but referenced by the release build config —
   `./gradlew :app:assembleRelease` will fail until it exists. Debug builds are unaffected.
2. **`gradlew` is a hand-written stub**, not the official Gradle wrapper script. It functions,
   but lacks standard JVM-arg and error handling, and there is no `gradlew.bat`. I'll
   regenerate it with `./gradlew wrapper` once a build succeeds.
3. **The repo has zero commits and no remote.** Everything is untracked. We need a baseline
   commit before Phase 1 so each phase lands as a reviewable diff.
4. `org.gradle.configuration-cache=true` in `gradle.properties` is aggressive and interacts
   badly with some AGP tasks. If you hit odd cache errors, add `--no-configuration-cache`
   and tell me — I'd rather fix the root cause than leave it on by default.

---

## Working agreement for the phases

1. I write a phase into `narratrace-mobile-android/`.
2. You run `./gradlew :app:assembleDebug :app:testDebugUnitTest` and paste the output.
3. I fix whatever breaks.
4. Green build → you commit that phase → we start the next one.

I cannot compile Android in my environment (no SDK, JDK 11 only, Google/Maven repos are
network-blocked), so step 2 is not optional. Skipping it means accumulating unverified code,
which is how a 15,000-line codebase becomes unmaintainable before it ever runs.
