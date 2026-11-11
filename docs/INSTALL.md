# Install, try, recover

## Compatibility

Quiet Home requires Android API 28 or later (for example, compatible Fire OS 7/8 devices). Earlier Fire OS devices may not install it. Newer firmware can restrict third-party launchers and accessibility settings. Device support is not guaranteed; no automated changes are made to your TV.

Build from the root README, enable ADB debugging on the TV, and authorize your computer. Use ADB only on a trusted local network. Do not expose port 5555 to the internet. These commands assume one connected device; use `adb -s DEVICE` if you have several.

```sh
adb connect FIRE_TV_IP:5555
adb install -r dist/quiet-home.apk
adb shell am start -n com.jakedaltrui.quiethome/.MainActivity
```

Use directional buttons to navigate; Select launches an app or header control. Down expands All Apps.

## Optional: Home-button redirect

Try the app manually before enabling this. Fire OS often ignores standard default-home preferences. The optional accessibility service reacts to window-state changes from `com.amazon.tv.launcher` and opens Quiet Home. It cannot retrieve window content, read text, or inject gestures.

If your device offers the service in Accessibility settings, enable **Quiet Home button redirect** there. Do not disable or uninstall the Amazon launcher. If the service is unavailable or Fire OS blocks it, keep using manual launch.

For advanced users, the service component is:

```text
com.jakedaltrui.quiethome/.HomeRedirectService
```

This guide intentionally avoids overwriting `enabled_accessibility_services` with an ADB command, which could disable other accessibility tools. Manage services using the device UI.

## Optional: PIA widget

Install and sign in to Private Internet Access separately. Quiet Home never receives your VPN credentials. To allow binding its widget on a compatible device:

```sh
adb shell appwidget grantbind --package com.jakedaltrui.quiethome --user 0
```

Relaunch Quiet Home. This permission is optional. Without widget access, selecting the control attempts to open PIA instead. Widget support can change between PIA versions. Revoke it with:

```sh
adb shell appwidget revokebind --package com.jakedaltrui.quiethome --user 0
```

## Recovery and uninstall

Disable **Quiet Home button redirect** in Accessibility settings before returning to the stock launcher. Open Settings directly if needed:

```sh
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
```

Then uninstall Quiet Home if desired:

```sh
adb uninstall com.jakedaltrui.quiethome
```

Uninstalling deletes Quiet Home's local caches and preferences. It does not uninstall your streaming apps. Keep the stock launcher enabled so recovery stays simple. Turn off ADB debugging when finished.

## Troubleshooting

| Symptom | Next step |
| --- | --- |
| `INSTALL_FAILED_OLDER_SDK` | Device is below API 28; this build is unsupported |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Existing APK has a different signer; retain its key or uninstall first (loses Quiet Home preferences) |
| Home still opens Amazon | Your firmware may not support the optional redirect; use manual launch |
| PIA control opens the app | Widget is not available/bound; this is the fallback |
| No game scores | Check connectivity; public feeds can fail and previous values can remain cached |
| App tile is missing | Discovery is for non-system apps with a TV launcher activity; add a package to `BASE_APPS` and rebuild if needed |
