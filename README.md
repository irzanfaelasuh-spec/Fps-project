# FPS+

A native Android performance overlay project. The interface is designed around a dark graphite surface, restrained cyan accents, a nine-second animated intro, a guided permission setup, a live dashboard and a movable floating HUD.

## What is included

- **FPS+ identity and launcher icon:** custom adaptive vector icon, no external image or font assets.
- **Nine-second intro:** animated orbital rings, a restrained particle field and a progress indicator.
- **Permission setup:** overlay, background/battery-optimization exception, Shizuku authorization and notifications. Each row changes from amber to green when Android reports that it is enabled. The Next button animates upward only after all four are ready.
- **Floating HUD:** application overlay that stays above games when the system allows it. Drag the panel to move it. Its scale and opacity are configurable.
- **Selectable live fields:** FPS, RAM used/total, battery percentage, CPU/GPU temperature when exposed by the device (otherwise battery-sensor temperature), thermal status and foreground package. Use the FPS-only preset or combine fields.
- **Dashboard:** current FPS, a live frame trend, memory, battery and thermal cards, foreground package and monitor Start/Stop.
- **Settings:** panel fields, scale, opacity, compact mode, outline, refresh interval and position reset.
- **Shizuku frame probe:** a narrow AIDL UserService runs read-only `dumpsys activity` and `dumpsys SurfaceFlinger` queries under Shizuku's shell/root identity.
- **GitHub Actions:** runs unit tests, builds the debug APK and uploads it as a workflow artifact.
- **Local-first:** no Firebase, account, analytics SDK or remote telemetry is included.

## Build the APK

### GitHub Actions

1. Create a repository and upload the contents of this folder to its root (the `.github/workflows/android.yml` file must remain at that exact path).
2. Push to `main` or `master`, or open **Actions → FPS+ Android CI → Run workflow**.
3. Open the successful workflow run and download the `FPSPlus-debug-apk` artifact.

The workflow provisions JDK 17, Android SDK platform 35 and Gradle 8.9, runs `:app:testDebugUnitTest`, then assembles `:app:assembleDebug`.

### Android Studio or terminal

Open this folder in Android Studio and let Gradle sync. From a terminal:

```bash
./gradlew :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:assembleDebug
```

The lightweight launcher script bootstraps Gradle 8.9 into the user's Gradle cache if it is not already present. Android SDK platform 35 and build-tools 35.0.0 are still required.

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## First-run setup on the phone

1. Install and open **Shizuku** from its official site: <https://shizuku.rikka.app/download/>.
2. Start Shizuku. On Android 11 and newer, the non-root route is Wireless debugging; pair/start it using the Shizuku app's own instructions. Shizuku started through ADB may need to be started again after a reboot.
3. Open FPS+. Grant **Display over other apps**, **Background operation** (battery-optimization exception) and **Notifications**.
4. Tap **Shizuku access** in FPS+ and approve the Shizuku permission dialog.
5. When all four setup rows are green, tap **Next**.
6. Open the Overlay tab to choose which telemetry fields appear. Tap **Start Monitor** on Overview. The panel can be dragged while it is visible.

Some Android manufacturers add separate Auto-start / Background activity controls. Those controls are vendor-specific and cannot be granted automatically by a normal app; enable them manually for FPS+ if the phone's system manager provides them.

## FPS measurement: important limits

Android does not expose a universal public API that lets an ordinary third-party app read the true FPS of every other app. This project uses Shizuku's UserService and samples frame timestamps from the active app's SurfaceFlinger layer (`dumpsys SurfaceFlinger --list` and `--latency`). The estimate is based on recent present timestamps; it is not a fabricated counter and it is not an in-game engine hook.

FPS can show `--` on the dashboard or `0` in the compact HUD while:

- Shizuku is stopped or FPS+ has not been authorized;
- the game does not expose a matching SurfaceView/layer;
- the OEM Android build blocks the relevant SurfaceFlinger command;
- a game is in a loading screen or not producing enough frame samples.

Shizuku shell access is not equivalent to root. Whether a given `dumpsys` command works depends on Android version, OEM build and the privileges available to the Shizuku backend. The app therefore reports unavailable telemetry rather than substituting a guessed FPS value.

The other readings also have precise meanings:

- **RAM:** system-wide used memory estimated from `ActivityManager.MemoryInfo` (`totalMem - availMem`), not only memory used by the game.
- **Battery:** current percentage from Android's sticky battery broadcast.
- **Temperature:** FPS+ tries Android `dumpsys thermalservice` through Shizuku and uses the highest available CPU sensor reading (or GPU if no CPU sensor is exposed). If those sensors are unavailable, it falls back to the battery temperature sensor. OEMs can omit or rename sensors, so this value may still be unavailable or represent the battery.
- **Thermal:** Android's thermal-throttling status category on API 29+. It is not a Celsius temperature.

## Android requirements

- Minimum Android version: Android 8.0 (API 26).
- Compile / target SDK: 35.
- FPS sampling: Shizuku v11+ (UserService support); Shizuku itself must be started by the user.
- Floating HUD: Android's special app access **Display over other apps**.
- Continuous monitoring: a foreground service with a persistent notification. Android 14+ requires an explicit foreground-service type; this project declares `specialUse` for its user-visible floating performance monitor.

## Project layout

```text
FPSPlus/
├── .github/workflows/android.yml
├── app/
│   ├── src/main/aidl/.../IFrameProbe.aidl
│   ├── src/main/java/com/novaforge/fpsplus/
│   │   ├── MainActivity.kt
│   │   ├── monitor/FrameProbeService.kt
│   │   ├── monitor/FpsEstimator.kt
│   │   ├── monitor/MonitorService.kt
│   │   ├── monitor/MonitorState.kt
│   │   └── ui/Screens.kt, Theme.kt
│   └── src/test/java/.../FpsEstimatorTest.kt
├── build.gradle.kts
└── settings.gradle.kts
```

## Verification note

The project includes a GitHub Actions build and focused FPS-estimator unit tests. This workspace does not contain an Android SDK or an attached Android device, so an APK could not be compiled or device-tested here. The first GitHub Actions run is the authoritative build check; real FPS support must then be verified on the target phone/game because SurfaceFlinger exposure varies by OEM.
