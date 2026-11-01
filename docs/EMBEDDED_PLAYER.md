# In-app playback (v0.3.0)

The standalone YouTube TV handoff is replaced by a private `PlayerActivity` containing Android's OS WebView and the **official YouTube IFrame Player API**. Videos play inside HackerTube. No installed YouTube TV app is needed and there is no automatic external-app fallback.

## Official documentation consulted

- [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference): play/pause, seeking, state/error callbacks, autoplay blocking, and Android WebView Media Integrity.
- [YouTube required minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality#embedded-player-api-client-identity): OS-provided WebView, app identity/referrer, minimum viewport size, visible player, and prohibition on covering the embedded player.
- [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview): WebView settings and navigation handling.
- [Android TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality): media remote controls, no background video playback, appropriate ambient-mode handling, and no external browser launch.

## Implementation

- Bundled `assets/player.html` uses the official API script and `youtube-nocookie.com` embedded host. Original YouTube controls and branding are retained; no ad blocking, DOM manipulation of YouTube internals, stream extraction or video proxy.
- `loadDataWithBaseURL("https://app.hackertube.tv/", ...)` supplies the real OS-installed application ID as the origin/referrer, per YouTube's explicit instructions for sideloaded applications. The same origin is supplied in player parameters. This is not impersonation of hackertube.org or another website.
- Validated 11-character video ID, JSON-encoded substitutions, private/non-exported activity. No `addJavascriptInterface` native bridge. State is polled from the local wrapper via `evaluateJavascript`; the remote iframe cannot directly invoke Android APIs.
- File/content access and mixed content disabled, no popup or external URL intents, standard certificate validation. Third-party cookies and DOM storage are enabled for the embedded player; privacy-enhanced host is not a claim of zero data collection.
- Buttons and status sit **outside** the embedded player viewport. Cinema mode hides those native rows, enlarging the WebView without covering YouTube. YouTube's own controls remain available through the explicit “YouTube controls” action. No custom fullscreen overlay is placed over the video.
- Remote media keys and a foreground MediaSession use actual IFrame playback state. Native player focus maps center to toggle, left/right to ±10 seconds, down to native controls. In YouTube-controls mode, D-pad events pass through to the iframe.
- Keep-screen-on is enabled only for active playing/buffering and cleared on pause/background/error. No background audio service or notification. WebView owns audio focus; a competing native AudioManager request was tested and removed because it immediately interrupted Chromium's playback.
- On pause, the wrapper is told to pause. On stop, WebView is destroyed, ensuring playback cannot continue off-screen. Returning reloads near the last polled timestamp without autoplay. Configuration recreation uses saved position/cinema mode. Position is approximate and not persisted as watch history.
- Handles missing WebView, render-process failure, load timeout, autoplay blocking, and YouTube error codes 2/5/100/101/150/153. Retry stays in the app. Explicit Watch is the only initial autoplay trigger.

## Validation

- Actual 3Blue1Brown “Vectors” video played in the Android 14 Google TV ARM64 emulator. Media position advanced while `app.hackertube.tv/.PlayerActivity` remained the foreground activity.
- Live regression script verified advancing position, pause, 10-second seek, resume, cinema D-pad behavior, Home stopping playback, no automatic playback on return, and Back to the original native detail dialog.
- Seven deterministic Node tests exercise the JavaScript adapter with a mocked official API, including ready/background races, no automatic resume, seek bounds and error 153 forwarding. These tests run in CI without contacting YouTube.
- The existing 13 catalog/resource tests and catalog D-pad suite remain applicable.
- [Player screenshot](screenshots/google-tv-player.png) · [Cinema screenshot](screenshots/google-tv-player-cinema.png).

Run:
```sh
node --test scripts/test_player.mjs
python3 scripts/smoke_player.py --serial emulator-5554
```
The live script depends on YouTube availability, the network and the video's embed permissions, so it is not part of deterministic CI.

## Limitations / not claimed

- Physical Google TV hardware testing is still pending. WebView implementation, decoding, network restrictions and device attestation differ by TV.
- No guarantee that all 2,074 videos are currently available or embeddable. Error handling is implemented; actual examples of every YouTube error have not all been exercised live.
- YouTube owns the embed's ads, captions/settings UI, consent, age/region/account restrictions and related-video suggestions. `rel=0` does not remove related suggestions. HackerTube's catalog is curated; the unmodified YouTube UI is not an isolated/kiosk player.
- The iframe may expose controls that are less convenient with a remote; original captions/settings D-pad behavior still needs physical-TV and TalkBack evaluation.
- Login/Premium in the standalone YouTube TV app does not automatically transfer to WebView. No account login flow or restriction bypass is implemented.
- MediaSession and player state now exist in HackerTube, superseding the delegated-playback statements in the historical v0.2 TV design review. Watch Next, persistent progress and cross-device syncing remain unimplemented.
