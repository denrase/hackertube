# Development notes

This is an unsupported personal experiment. Start with the [README](../README.md) for project credit, features and licensing.

## Build

Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. For example, with Homebrew Java on macOS:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Minimum Android 6; TV/Leanback required. This is a debug-signed sideload build, not a production release. Public binary distribution needs a deliberate signing/update strategy and policy review.

## TV installation

Enable developer options by clicking **Settings → System → About → Android TV OS build** seven times. Enable network/wireless debugging; names vary by manufacturer.

- Wireless pairing: `adb pair TV_IP:PAIRING_PORT`, enter the code locally, then connect using the separate debugging port.
- Legacy network ADB: `adb connect TV_IP:5555`, then accept authorization on the TV.
- Some TVs require initial USB setup instead.

```sh
scripts/install-tv.sh TV_IP:DEBUG_PORT
```

Disable debugging afterward; never expose ADB to the internet. On the development Mac, platform-tools 37 crashed with mDNS enabled. `export ADB_MDNS=0` worked for explicit-address connections.

## Data refresh

The bundled catalog is derived from HackerTube's 2026-10-31 export. We reuse its analysis and keep only videos whose exported `rec` / `strong_rec` evidence includes three distinct authors, three distinct months, and two distinct calendar years; `news` is excluded. Calendar years do not imply a full 24 months. Evidence is capped at 20 mentions per video, so filtering is conservative. Source labels and summaries are AI-generated and may be wrong.

```sh
curl -fL https://hackertube.org/download/videos.jsonl -o /tmp/videos.jsonl
curl -fL https://hackertube.org/download/mentions.jsonl -o /tmp/mentions.jsonl
python3 scripts/import_catalog.py /tmp/videos.jsonl /tmp/mentions.jsonl
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Review upstream license/schema changes and update provenance, counts and attribution when refreshing. See [upstream documentation](DATA.md) and [NOTICE](../NOTICE.md).

## Tests

```sh
python3 -m unittest discover -s scripts -p 'test_*.py' -v
node --test scripts/test_player.mjs
./gradlew testDebugUnitTest
```

On a disposable emulator with the APK installed:

```sh
# Clears that emulator's app data and temporarily changes font scale:
python3 scripts/smoke_tv.py --serial emulator-5554
# Live, network-dependent YouTube playback test; not run in CI:
python3 scripts/smoke_player.py --serial emulator-5554
```

Current coverage: 13 Python catalog/resource tests, 11 JavaScript player-adapter tests, and five JVM duration-formatting tests. Emulator checks cover D-pad navigation, saved/empty states, search, pagination, topics, focus restoration, 130% text, playback progression, transport controls, subtitle selection, cinema mode and background/Back behavior. These are not a full device compatibility, accessibility or policy audit.

## Player behavior

The native toolbar provides Play/Pause, ±10 seconds, Subtitles, YouTube controls and Cinema. The subtitle picker pauses playback and resumes only if it was previously playing. Its displayed selection does not track subsequent manual changes in YouTube's own controls.

YouTube controls gives the original iframe focus for its settings; Back returns to native controls. Otherwise Back returns to video details. Home stops playback. Returning reloads near the last observed position without autoplay. Persistent watch-history syncing isn't implemented.

See [embedded-player design and limitations](EMBEDDED_PLAYER.md) and [the historical TV design review](TV_DESIGN_REVIEW.md).

## Privacy and external dependencies

HackerTube TV has no first-party analytics, accounts or backend. Saved IDs remain in local app preferences and are removed on uninstall.

- Thumbnails and embedded playback contact YouTube.
- Visible video IDs are sent to `hackertube.org` for duration metadata. Successful values are cached locally and considered fresh for up to 30 days. Unknown runtimes are not invented. This optional request isn't needed to browse the bundled catalog.
- The embedded player uses WebView storage/cookies and may use WebView Media Integrity attestation, including app metadata and a device token. YouTube's own policies apply.
- WebView does not inherit login/Premium state from the standalone YouTube TV app.
- HN source URLs are displayed for reading on another device; the app doesn't launch a browser.

This description is not a complete public-release privacy policy or policy clearance. Known review items include blocked player links, runtime-exposed caption options, hosted metadata API permission, expired-cache deletion and the required consent/terms flow.
