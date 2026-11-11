# Test plan and current verification

## Automated checks included

Run `python3 scripts/validate_project.py`. It checks:

- JSON configuration parsing and required fields.
- Python backend syntax compilation.
- Dashboard JavaScript syntax (when Node.js is installed).
- Android manifest/styles XML well-formedness.
- ZIP contents/integrity when validating the final archive.

## Manual TV acceptance tests

1. Install APK with ADB; confirm HomeOS appears in TV launcher apps.
2. Press Home; confirm Android offers HomeOS as a selectable Home app.
3. Select HomeOS temporarily; verify app cards are visible and D-pad focus is obvious.
4. Launch an installed TV app; press Back/Home and verify return behavior on the specific TV.
5. Install/uninstall an app and verify app list refresh after package broadcast or relaunch.
6. Start Pi backend; set URL/token in Settings; verify `PI ONLINE` and remote widget/page data.
7. Stop backend and restart launcher; verify cached pages remain.
8. Publish a new page from dashboard; sync; verify page appears without APK reinstall.
9. Publish invalid JSON/widget type; verify server rejects it and TV keeps previous cache.
10. Test on actual remote for focus navigation across app row, page tabs, and WebView widgets.

## Not verified in this environment

No Android SDK, Android emulator, connected TV, or Gradle installation was available in the build container. Therefore APK compilation, installation, device focus behavior, vendor launcher replacement, and real PackageManager discovery are not claimed as passed. These checks require Android Studio/SDK and the target device.
