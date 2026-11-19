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

## Why it exists

"Offline" is a checkable fact on Android, in two halves: the `INTERNET` permission in
an app's manifest says *can it* reach the network, and Android's per-app network
statistics say *how much* it does. Offline Checker answers both halves in one
list — and takes a stance on each:

- **The checker itself has no `INTERNET` permission.** The verdict an app without
  `INTERNET` gets is enforced by the OS, and the checker holds itself to the same
  standard you can verify in its own
  [manifest](app/src/main/AndroidManifest.xml): Android blocks every network call it
  could ever make. It does not even request `QUERY_ALL_PACKAGES` — the
  Play-restricted visibility permission most "app manager" apps ask for — accepting
  Android 11's package-visibility filter instead of shipping a capability it does not
  need. What it shows you is what your device can show any app.
- **No third-party libraries, one Activity, plain platform views.** The entire
  codebase is small enough to audit in a single sitting, and the MIT-licensed source
  is the same code that builds the release APK.
- **Numbers come from Android's own accounting.** Totals are read on-device from
  `NetworkStatsManager` — the same per-app attribution the system's Data usage screen
  shows in aggregate. Nothing is uploaded, nothing is inferred, nothing leaves the
  device.

The result is a tool whose trust model you can check the same way it checks everyone
else's apps.

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

## Contributing

Pull requests are welcome at
[github.com/octoflashdev/offline-checker](https://github.com/octoflashdev/offline-checker),
targeting `main`. This covers the app code, README, app strings, and
`fastlane/metadata/android` text (F-Droid reads that directory from this repo, so
changes here flow through to the F-Droid listing).

The F-Droid packaging recipe (`metadata/com.offlinechecker.yml` on fdroiddata, mirrored
in `fdroid/`) is maintainer-only; it is updated through merge requests against
[fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata), not GitHub pull requests.

Report bugs in the
[GitHub issue tracker](https://github.com/octoflashdev/offline-checker/issues).

## License

MIT
