# HomeOS TV Runtime 1.0.0

A one-time Android TV native shell with a Hermes-updatable local WebView runtime.

## Target
- Package: `com.homeos.tv`
- Version: `1.0.0` (versionCode 100)
- minSdk: 23 (Android 6+; includes Android 9)
- targetSdk: 28
- Java 17 / AGP 8.7.3 / Gradle 8.9
- AndroidX WebKit 1.15.0 (minSdk 23 compatible)
- No NDK or native ABI-specific binaries; intended to work on ARM32/ARM64 if the TV's Android WebView provider is functional.

## Build
Open this directory in Android Studio with JDK 17 and Android SDK 35, then build `app` > `assembleDebug`. On Windows PowerShell, run `./build.ps1` if Gradle 8.9 is on PATH. Or use Gradle 8.9:

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## First setup
The launcher defaults to HomeOS Pi base URL `http://192.168.254.54:8081`. The app first renders bundled local content. Hermes must implement the runtime endpoints documented in `docs/RUNTIME-PROTOCOL.md` to publish future updates. The existing `/health` endpoint alone is not enough to deliver runtime updates.

## Important
This is the initial shell/runtime foundation, not a finished full HomeOS dashboard. Dynamic UI updates are supported by downloading validated static web-runtime bundles. Native Android features not already exposed by the bridge require a future APK update.
