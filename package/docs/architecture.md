# Architecture notes

## Runtime split

- Native Kotlin owns Home intent handling, package discovery, app launch, remote focus, settings, network sync, and cache persistence.
- WebView renders bundled local HTML only. It is intentionally not pointed at the Raspberry Pi dashboard. Remote JSON is fetched by native code and injected as serialized data.
- Dashboard/backend own page/widget configuration and publish versioned JSON.

## Why no JavaScript native bridge

The renderer needs no privileged native bridge in this baseline. This reduces the attack surface: the web page receives display data only. Android's current guidance recommends origin-scoped `WebMessageListener` when a bridge is necessary and warns against exposing `addJavascriptInterface` to untrusted frames. See Android references in the README and source list.

## Package discovery

The app queries `LEANBACK_LAUNCHER` activities first, then `LAUNCHER` activities. A broad launcher use case may need `QUERY_ALL_PACKAGES`; Play distribution requires policy review. System/vendor apps without exported launchable activities cannot be launched by a third-party launcher.

## Dynamic update boundary

Remote JSON can rearrange supported components and pages. It cannot add new Kotlin code, Android permissions, manifest entries, or unrestricted native capabilities. Add new capabilities by extending the native runtime and publishing a new APK.

## Offline policy

The last schema-valid configuration is stored in SharedPreferences. A failed network request never overwrites it. App discovery and launching are always local.
