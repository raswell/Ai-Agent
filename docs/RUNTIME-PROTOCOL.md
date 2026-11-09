# HomeOS TV Runtime Protocol v1

The Android package is a stable native shell. Hermes evolves the web runtime without rebuilding the APK.

## Server endpoints

The app uses the configured base URL (default `http://192.168.254.54:8081`). The HomeOS server must expose:

- `GET /health` — existing health endpoint; JSON must include `{"status":"ok"}`.
- `GET /launcher/v1/manifest.json` — runtime manifest.
- `GET /launcher/v1/bundle.zip` — ZIP bundle referenced by `bundleUrl`.

Manifest example:

```json
{
  "schemaVersion": 1,
  "version": "1.0.1",
  "versionCode": 101,
  "minShellVersion": 1,
  "bundleUrl": "/launcher/v1/bundle.zip",
  "sha256": "<64 lowercase hex characters>"
}
```

`versionCode` must be a strictly increasing integer. `minShellVersion` must not exceed the installed shell version code (currently `1`). The ZIP root must contain `index.html` and may contain `css/`, `js/`, `modules/`, `images/`, `config.json`, and other static UI assets. The bundled module host renders module metadata and section/item schemas from `config.json`; Hermes can also add new HTML/JS/CSS module routes inside the bundle. The runtime is loaded from the TV's private app storage after validation. The updater checks the bundle SHA-256, rejects ZIP path traversal, enforces download/expanded-size limits, stages the new runtime, preserves the previous runtime, and atomically switches directories.

## Update behavior

- A local bundled runtime is copied on first launch.
- The app renders the local runtime first; update checks happen in the background.
- A failed fetch or invalid bundle leaves the current runtime untouched.
- A successful update reloads the WebView.
- Runtime content remains available when the Pi is offline.

## Native bridge contract

Only the following narrow methods are exposed to the WebView:

- `getShellVersion()`
- `getServerBase()`
- `getRuntimeVersion()`
- `getAppsJson()`
- `launchApp(packageName)` — launches an installed package with a launcher activity.
- `setServerBase(url)`
- `refreshRuntime()`

Do not add arbitrary Intent execution, shell execution, filesystem access, or unrestricted native method dispatch to the bridge.

## Integrity limitation

SHA-256 detects corruption and accidental mismatch; it does not authenticate the server because the manifest and hash are served by the same endpoint. For a hostile-network threat model, add a pinned public-key signature to the manifest and verify it in the shell before activation. Keep the signing private key only on the Raspberry Pi. Do not treat plain HTTP plus SHA-256 as cryptographic server authentication.

## Module conventions

Modules are HTML/CSS/JavaScript content within the runtime bundle. They cannot add new Android permissions, native services, or arbitrary Java/Kotlin code. A feature needing a new native capability requires a deliberate shell/API update and therefore a new APK.
