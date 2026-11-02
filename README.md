# HackerTube for Google TV

> [!WARNING]
> **This was AI-slopped together for my own TV. It is not a maintained product.**
>
> Published as-is because someone else might find it useful. Expect bugs, rough edges, brittle integrations, and things that stop working. I am **not committing to support, fixes, compatibility updates, security updates, or reviewing issues and pull requests for other users**.
>
> **Fork it and make it yours.** The original application code is MIT-licensed: use, modify, redistribute, or commercialize it subject to that license. The bundled dataset and third-party content have separate terms—see [NOTICE.md](NOTICE.md). You are responsible for reviewing those terms and the YouTube/platform requirements before using or distributing your fork. This disclaimer is not a claim of policy compliance.

An unofficial, remote-friendly Android TV catalog of videos repeatedly recommended on Hacker News. Personal experiment inspired by [Karpathy's post](https://x.com/karpathy/status/2116957135661945283).

## License
Original application code: [MIT](LICENSE). Bundled HackerTube-derived data: **ODC-By 1.0**, with attribution and details in [NOTICE.md](NOTICE.md). Third-party code, trademarks, videos, thumbnails, and other imagery are not relicensed by this project's MIT license.

## Included
- 2,074 curated videos, bundled locally; no backend of your own or API key required.
- Duration badges on thumbnails and runtimes in details, fetched for visible videos from HackerTube's public metadata API and cached locally for up to 30 days.
- D-pad navigation, orange focus rings, selected tabs, search, topic filtering and paginated browsing.
- TV-safe margins, proportional thumbnails and flexible cards tested at 130% system text size.
- Predictable Back-to-menu navigation and focus restoration after details, pagination and configuration changes.
- Device-local saved videos.
- Recommendation evidence: author, month, quote and link to the original HN comment.
- **In-app video playback** through YouTube's official embedded player, with remote play/pause, ±10-second seeking and full-screen cinema mode.
- Subtitles start Off for each player session; a native picker offers the video's available tracks and Off.
- Back returns to video details; playback stops when you leave the app. No external app/browser fallback.
- Offline catalog browsing; thumbnails and playback require internet.

Playback stays in HackerTube, using Android System WebView and YouTube's IFrame API—not the separate YouTube TV app. YouTube still controls its embedded UI, related-video suggestions, ads, captions, availability and playback restrictions. Videos that disallow embedding cannot play here; errors stay in the app. No scraping, stream extraction, player masking or ad bypass.

### Player remote controls
- **Watch** on a video detail page starts in-app playback.
- The native toolbar provides **Play/Pause**, **−10 sec**, **+10 sec**, **Subtitles**, **YouTube controls**, and **Cinema**.
- In **Cinema**, center plays/pauses, left/right seek 10 seconds, and down restores the toolbar. Native controls sit outside the YouTube viewport, never over it.
- **Subtitles** pauses playback while you select Off or an available language, then resumes if it was playing. The picker depends on caption options exposed by YouTube; if unavailable, use the original CC/settings controls. The toolbar reflects the selection made in HackerTube, not subsequent manual changes inside YouTube's own controls.
- **YouTube controls** gives focus to the original player UI for captions/settings. Back returns to native controls; otherwise Back returns to video details.
- Dedicated remote media keys work too. Leaving for Home pauses and destroys the player; returning reloads near the last observed position without autoplay. Persistent watch-history syncing is not implemented.
- A current Android System WebView is required. The standalone YouTube TV app is not required.

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
- Thirteen Python tests cover recommendation filtering, bundled catalog invariants and TV manifest/launcher assets.
- Android debug compilation and lint pass (non-blocking English-only UI, TV orientation and monochrome-icon warnings).
- Automated Android 14 Google TV D-pad regression checks cover browsing, source dialogs, saved/empty states, search, pagination, topics, focus restoration, 130% text and Back-to-Home.
- Eleven Node tests cover the embedded adapter's initialization, identity, play/pause, seek bounds, lifecycle, subtitle selection and error reporting.
- Five JVM tests cover duration formatting and unknown-duration handling.
- Live emulator playback confirmed actual advancing video time, pause/resume, seeking, default-Off subtitles, German selection and Off again, cinema controls, stopping on Home, and returning to details without leaving HackerTube.
- Physical-TV playback, TalkBack and every YouTube restriction/error scenario still require device testing.

Run UI checks on a disposable emulator (clears only that emulator's app data):
```sh
python3 scripts/smoke_tv.py --serial emulator-5554
node --test scripts/test_player.mjs
./gradlew testDebugUnitTest
# Live network-dependent playback test (not run in CI):
python3 scripts/smoke_player.py --serial emulator-5554
```

See [official-guideline research and applied changes](docs/TV_DESIGN_REVIEW.md) and [embedded player design and testing](docs/EMBEDDED_PLAYER.md).

## Privacy / limitations
HackerTube has no first-party analytics, accounts, backend, or secrets. Saved IDs stay in app preferences and are removed on uninstall. Thumbnails and playback contact YouTube. Visible video IDs are sent to hackertube.org to obtain duration metadata; that optional request is not needed to browse the bundled catalog. Unknown/offline durations are never invented. The embedded player uses WebView storage/cookies and may use WebView Media Integrity attestation (app metadata and a device token); YouTube's own policies apply. It does not inherit login/Premium state from the YouTube TV app. HN source URLs are displayed for reading on another device; the app never launches a web browser. Catalog updates require rebuilding. UI is English-only. No persistent watch-progress syncing.
