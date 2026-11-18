# Offline Checker

A tiny single-Activity Android app that shows which visible apps hold the `INTERNET`
permission and how much data the ones that do send and receive.

- Lists visible installed apps and marks each one. Apps with `INTERNET` show received
  and sent traffic for the last 24 hours or 7 days (7 days by default). Apps without it
  show **No INTERNET permission**.
- The default usage sort orders apps with `INTERNET` by total traffic, then the rest by
  name. The sort badge next to the range control opens a Usage/Name dropdown; Name sorts
  every app alphabetically.
- System apps are hidden by default; **Show system apps** in the top bar includes them.
- No ads, no trackers, no libraries; one Activity, plain platform views.

## Permissions

The manifest declares exactly one permission:

- `android.permission.PACKAGE_USAGE_STATS` — Android's special "Usage access" grant.
  Enable it in Settings → Apps → Special app access → Usage access → Offline Checker.
  The app's button opens this screen. Android does not expose other apps' network
  statistics to apps without it.

**Offline Checker has no `INTERNET` permission and cannot reach the network.** All
checks and statistics are read locally from Android's own `PackageManager` and
`NetworkStatsManager` and shown on screen.

The manifest also contains a `<queries>` element for `MAIN`/`LAUNCHER` intents. It is not
a permission; it restores visibility of launchable apps on Android 11+, where package
visibility is filtered by default.

## Limits

- **Visibility:** Android 11+ hides most background/system apps unless an app requests
  `QUERY_ALL_PACKAGES` (restricted by Google Play, possible for sideload/F-Droid builds).
  Offline Checker deliberately does not request it, so apps outside `<queries>` visibility
  — for example Google Play Services on devices that have it — are absent from the list,
  and neither their permission status nor their traffic is shown. Uncomment the permission
  noted in the manifest for a complete list.
- Byte totals only. Android does not expose destinations (domains, IPs, URLs) to normal
  apps; that requires a local VPN.
- Android keeps network history for a limited time, so a 7-day window can be partially
  empty on some devices.
- Attribution can differ from carrier billing, especially with VPNs, tethering, and
  shared UIDs (apps sharing a UID are counted together and labeled with one package
  name).
- Windows are rolling periods, not calendar days.

## Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio. Requires JDK 17+ and Android SDK 35.

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add the user-visible notes as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, tag `v<versionName>`, and push the branch and tag.
4. The first F-Droid release is submitted by copying `fdroid/com.offlinechecker.yml`
   into an fdroiddata merge request; F-Droid then builds and signs from source.
   Later versions are picked up from tags automatically.

## License

MIT
