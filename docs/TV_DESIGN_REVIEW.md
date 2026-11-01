# Google TV design and engineering review

Research and application: 2026-11-01. App revision: 0.2.0.

**Historical review:** v0.3.0 replaces the delegated YouTube TV playback described below with an official in-app embed. Playback, MediaSession and ambient-mode statements below are superseded by [the embedded-player design](EMBEDDED_PLAYER.md); catalog/UI findings still apply.

This is a review against official Android documentation, **not Play Store certification**. Google TV applications run on Android TV OS, so these are the applicable design and quality guides.

## Sources and applied decisions

| Official source | Recommendation | Applied in HackerTube |
|---|---|---|
| [TV navigation](https://developer.android.com/training/tv/get-started/navigation) | Always show actionable focus; minimize steps; restore context; for top navigation Back from content returns to the menu; no exit gate or soft Back button | Startup focus on active Discover/Saved tab, persistent selected-tab styling separate from focus, card focus restored after details, safe fallback after removing the last saved item, first-card focus after pagination. Back moves grid → active tab → default Discover → system Home. Removed detail dialog's soft Back button. |
| [Build TV layouts](https://developer.android.com/training/tv/playback/compose/layouts#overscan) | Keep essential UI inside 48dp horizontal / 27dp vertical overscan margins; maintain full-screen backgrounds | Increased root safe-area margins, constrained detail-dialog bounds; full-bleed dark window background. Reduced header height to give content more space. |
| [TV cards](https://developer.android.com/design/ui/tv/guides/components/cards) | Use appropriate content aspect ratios, especially 16:9 for video; keep card descriptions concise | Width-derived 16:9 thumbnails with FIT_CENTER rather than arbitrary cropped height. Cards retain only title, channel and recommendation count/span; long summaries remain in details. |
| [TV accessibility](https://developer.android.com/training/tv/accessibility) | Respect system text scaling, flexible layout dimensions, useful assistive labels | Replaced fixed card/button heights with content-driven measurement and minimum button height; increased metadata from 12sp to 14sp. Grouped card accessibility into one action with full title, channel, recommendation count and years; marked decorative/repeated child nodes unimportant; labeled search and added polite result-count announcements. Verified layout at font_scale 1.3. |
| [TV app icons](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines) and [Create a TV app](https://developer.android.com/training/tv/start/start) | Both icon and banner; banner includes the app name; xhdpi banner 320×180 and icon at least 160×160; adaptive icon recommended | Replaced HT-only vector banner with full HackerTube wordmark. Added all five density variants, correct pixel sizes and a safe-zone adaptive launcher icon. Reproducible JDK-only asset generator in scripts/GenerateLauncher.java. Set launch color and transition-overlap theme attributes. |
| [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), TV-WB | Do not attempt to launch a browser for web content | Removed generic URL/browser fallback. HN evidence is native; source dialog displays a link to read on another device. Missing YouTube TV displays an explicit installation/enablement message. No WebView or embedded web browsing is needed. |
| [Optimize memory](https://developer.android.com/training/tv/playback/memory) | Budget for low-RAM TVs, bounded image allocations, release stale references, don't block startup on networking | 4 MiB image cache on low-RAM devices / 12 MiB otherwise, cache trim callbacks, separate two-thread thumbnail pool, clear pending obsolete requests on catalog change, generation checks reject stale results, debounce search by 250 ms, cancel workers on activity destruction. The existing 24-card page cap bounds view count. |
| [TV app quality](https://developer.android.com/docs/quality-guidelines/tv-app-quality), TV-BY | Do not keep a static catalog awake | No keep-screen-on flag or wake lock. YouTube owns playback lifecycle, media controls and ambient-mode behavior during playback. |

## Toolkit decision

[Compose for TV](https://developer.android.com/training/tv/playback/compose) is Google's recommended approach for new TV interfaces. Use TV Material components rather than phone Material components. Leanback is not the recommended migration destination.

This pass retains the existing platform Views app to make targeted, testable behavior changes rather than introduce an unverified full rewrite. Its grid is bounded to 24 cards, but not a recycling/lazy grid. A future UI modernization should move to **Compose for TV + LazyVerticalGrid**, preserve stable video keys and focus restoration, and rerun the remote-navigation tests. This is an explicit deferral, not a claim that a handwritten Views grid is Google's preferred architecture.

## Validation performed

- `./gradlew assembleDebug lintDebug`: builds and lint completes; no lint errors. Remaining warnings concern TV-only landscape orientation, English hardcoded UI strings and a monochrome adaptive icon (the TV icon guide explicitly says TV does not support themed icons).
- `python3 -m unittest discover -s scripts -p 'test_*.py' -v`: 13 tests, covering data invariants and TV manifest/launcher resource checks.
- `python3 scripts/smoke_tv.py --serial emulator-5554`: automated D-pad regression suite on Android 14 Google TV ARM64, 1920×1080 at 320dpi. The script intentionally clears app data only on a disposable emulator and restores the previous font scale afterward.
- Covered: initial focus, details, save/unsave, empty Saved state, source-link dialog without browser launch, originating-card restoration, search empty state, keyboard dismissal, filter reset, next-page focus, topic chooser, configuration-change focus restoration, 130% text layout, and finite Back navigation to system Home.
- Screenshots: [normal text](screenshots/google-tv-home.png), [130% text](screenshots/google-tv-large-text.png), [details at 130%](screenshots/google-tv-details-large-text.png).
- One post-navigation `dumpsys meminfo` snapshot: 81,804 KiB total PSS, 189,444 KiB RSS, 150 views, one activity. **Not** a peak-memory benchmark or low-RAM certification; emulator Graphics reported zero, so it cannot establish the complete official memory budget.
- No AndroidRuntime crash entries during the tested flows.

## Remaining work / limits

- Test a physical Google TV, including manufacturer remote behavior, overscan, keyboard/voice input, actual playback and return from YouTube.
- Conduct an actual TalkBack usability session; grouped labels and hierarchy inspection alone do not establish accessibility compliance. Test text sizes beyond 130% and more UI resolutions.
- Voice entry can use the system IME where available; no custom microphone or voice integration is claimed.
- No baseline profiles, adaptive banner, 4K thumbnail pipeline, Cast Connect, Engage SDK, Watch Next or deep-link catalog entry points yet. These are not necessary for this private first version. Watch-progress APIs must not be fabricated: playback is owned by YouTube and we cannot reliably observe completion.
- No MediaSession, player controls, captions or audio-description settings in HackerTube, because it does not play media itself. Those belong to the official YouTube TV player. Do not create a fake session to imply in-app playback.
- Local catalog remains static until rebuilt; source annotations are AI-generated, and video availability can change.
- This remains a debug-signed private sideload APK. Play distribution would require a signed release AAB and a full policy/quality review.
