# HackerTube for Google TV

A native, remote-friendly Android TV catalog of videos repeatedly recommended on Hacker News. Private personal project inspired by [Karpathy's post](https://x.com/karpathy/status/2116957135661945283).

## Included
- 2,074 curated videos, bundled locally; no backend or API key required.
- D-pad navigation, orange focus rings, search, topic filtering and paginated browsing.
- Device-local saved videos.
- Recommendation evidence: author, month, quote and link to the original HN comment.
- Playback handoff to the official YouTube TV app, with a standard URL fallback.
- Offline catalog browsing; thumbnails and playback require internet.

**This is a curated launcher, not an isolated YouTube player.** Once in YouTube, its recommendations, ads, availability and account settings apply. Some archived videos may be unavailable. Return behavior depends on the installed YouTube version. No scraping, stream extraction or ad bypass.

## Data and selection
Source: [HackerTube public export](https://hackertube.org/data), dated 2026-10-31. We reuse its video and labeled-comment exports, rather than scrape HN again. See [upstream documentation](docs/DATA.md) and [attribution](NOTICE.md).

A video qualifies only when its exported `rec` / `strong_rec` evidence includes:
1. At least three distinct authors.
2. At least three distinct months.
3. At least two distinct calendar years.
4. Not labeled `news`.

Calendar years do not imply a full 24 months. Exported evidence is capped at 20 mentions per video, so the filter is conservative. AI labels/summaries may be inaccurate. Rank is the original HackerTube score (recommendation months plus a strong-recommendation bonus). No freshness or duration claims are invented.

Refresh manually:
```sh
curl -fL https://hackertube.org/download/videos.jsonl -o /tmp/videos.jsonl
curl -fL https://hackertube.org/download/mentions.jsonl -o /tmp/mentions.jsonl
python3 scripts/import_catalog.py /tmp/videos.jsonl /tmp/mentions.jsonl
python3 -m unittest discover -s scripts -p 'test_*.py'
```
Review license/schema/date changes and update provenance when refreshing.

## Build
Requires JDK 17 and Android SDK platform 35 / build-tools 35.0.0.
```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug lintDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed personal sideload build, not a Play Store release. Minimum Android 6; TV/Leanback required.

## Install on Google TV
On the TV, enable developer options by clicking **Settings → System → About → Android TV OS build** seven times. Enable USB/network debugging (exact names vary by manufacturer).
- For wireless pairing: `adb pair TV_IP:PAIRING_PORT`, enter the code locally, then `adb connect TV_IP:DEBUG_PORT`.
- For legacy network ADB: `adb connect TV_IP:5555`, then accept the TV authorization prompt.
- Some TVs require initial USB ADB setup and do not expose network debugging.

Then:
```sh
export PATH="$ANDROID_HOME/platform-tools:$PATH"
scripts/install-tv.sh TV_IP:DEBUG_PORT
```
Disable debugging when finished. Don't expose ADB to the internet. On this development Mac, platform-tools 37 crashed with mDNS enabled; `export ADB_MDNS=0` fixes explicit-address connections.

## Validation
- Eight Python tests cover recommendation filtering and all bundled catalog invariants.
- Android debug compilation and lint pass (non-blocking English-only UI, banner size and TV orientation warnings).
- Manually smoke-tested on the Android 14 Google TV ARM64 emulator: catalog rendering, D-pad detail navigation, saved preference persistence, and launch of the official YouTube TV activity.
- Real-TV installation and real video playback still require device testing.

## Privacy / limitations
No analytics, accounts, backend, or secrets. Saved IDs stay in app preferences and are removed on uninstall. Thumbnail requests go to YouTube. HN links open a browser if installed; otherwise the URL is displayed. Catalog updates require rebuilding. UI is English-only. No watch-progress syncing or embedded playback.
