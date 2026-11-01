package app.hackertube.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** Official YouTube IFrame API. No stream extraction, native JS bridge, or external-app fallback. */
public final class PlayerActivity extends Activity {
    private static final int ACCENT = Color.rgb(255,119,48);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private LinearLayout root, controls, footer;
    private FrameLayout viewport;
    private TextView heading, status;
    private Button toggle, retry;
    private String videoId, videoTitle, nativeError = "";
    private boolean active, ready, cinema, iframeControls, autoStart = true;
    private double position, duration;
    private int playerState = -1;
    private long loadStarted;
    private MediaSession session;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active || web == null) return;
            WebView current = web;
            current.evaluateJavascript("JSON.stringify(window.htSnapshot ? htSnapshot() : null)", result -> {
                if (!active || web != current) return;
                try {
                    Object decoded = new JSONTokener(result).nextValue();
                    if (!(decoded instanceof String)) throw new IllegalStateException("Player not loaded");
                    if (decoded instanceof String) {
                        JSONObject data = new JSONObject((String) decoded);
                        boolean becameReady = !ready && data.optBoolean("ready");
                        ready = data.optBoolean("ready");
                        if (becameReady && !cinema && !iframeControls) toggle.requestFocus();
                        playerState = data.optInt("state", -1);
                        if (ready && playerState != -1 && playerState != 5) position = data.optDouble("time", position);
                        duration = data.optDouble("duration", duration);
                        int error = data.optInt("error");
                        keepAwake(ready && error == 0 && (playerState == 1 || playerState == 3));
                        toggle.setText(playerState == 1 ? "Pause" : "Play");
                        if (!nativeError.isEmpty()) showStatus(nativeError);
                        else if (error != 0) { showStatus(errorMessage(error)); if (cinema) revealControls(); }
                        else if (data.optBoolean("blocked")) { showStatus("Press Play to start. YouTube blocked automatic playback."); if (cinema) revealControls(); }
                        else if (ready) showStatus(stateLabel() + "  ·  " + clock(position) + " / " + clock(duration));
                        else if (SystemClock.elapsedRealtime() - loadStarted > 30000) { showStatus("YouTube did not load. Check your connection and Android System WebView, then Retry."); if (cinema) revealControls(); }
                        retry.setVisibility(error != 0 || !nativeError.isEmpty() || (!ready && SystemClock.elapsedRealtime()-loadStarted > 30000) ? View.VISIBLE : View.GONE);
                        updateSession();
                    }
                } catch (Exception ignored) {
                    if (SystemClock.elapsedRealtime()-loadStarted > 30000) {
                        showStatus("YouTube did not load. Check your connection and Android System WebView, then Retry.");
                        if (cinema) revealControls();
                        retry.setVisibility(View.VISIBLE);
                    }
                }
            });
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        videoId = getIntent().getStringExtra("videoId");
        videoTitle = getIntent().getStringExtra("title");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) { finish(); return; }
        if (videoTitle == null) videoTitle = "HackerTube";
        if (state != null) { position = state.getDouble("position"); cinema = state.getBoolean("cinema"); autoStart = false; }
        // WebView owns audio focus. A second native AudioManager request competes with Chromium
        // and immediately pauses its playback when the embedded media requests focus.
        session = new MediaSession(this, "HackerTubePlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { command("play"); }
            @Override public void onPause() { command("pause"); }
            @Override public void onStop() { command("pause"); }
            @Override public void onFastForward() { command("forward"); }
            @Override public void onRewind() { command("rewind"); }
        }, handler);
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        heading = text(videoTitle, 18);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(dp(48), dp(12), dp(48), dp(8));
        root.addView(heading);
        viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(48), dp(8), dp(48), dp(27));
        root.addView(footer);
        status = text("Loading YouTube…", 14);
        status.setMaxLines(3);
        footer.addView(status);
        controls = new LinearLayout(this);
        HorizontalScrollView controlScroll = new HorizontalScrollView(this);
        controlScroll.setHorizontalScrollBarEnabled(false);
        controlScroll.addView(controls);
        footer.addView(controlScroll);
        toggle = button("Play", () -> command("toggle"));
        button("−10 sec", () -> command("rewind"));
        button("+10 sec", () -> command("forward"));
        button("YouTube controls", () -> {
            if (web == null) return;
            iframeControls = true;
            web.setFocusable(true);
            web.requestFocus();
            command("focus");
            showStatus("YouTube controls: use the D-pad. Back returns to HackerTube controls.");
        });
        button("Cinema", () -> { cinema = true; applyCinema(); if (web != null) web.requestFocus(); });
        retry = button("Retry", () -> { destroyPlayer(); nativeError = ""; autoStart = true; createPlayer(); startPolling(); });
        retry.setVisibility(View.GONE);
        applyCinema();
        toggle.requestFocus();
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(14); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setMinHeight(dp(48)); b.setPadding(dp(12), dp(6), dp(12), dp(6));
        b.setBackground(buttonBackground(false));
        b.setOnFocusChangeListener((v, focused) -> v.setBackground(buttonBackground(focused)));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, dp(4), dp(8), 0);
        controls.addView(b, p);
        return b;
    }

    private GradientDrawable buttonBackground(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.rgb(27,32,42)); d.setCornerRadius(dp(8));
        if (focused) d.setStroke(dp(3), ACCENT);
        return d;
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(Color.WHITE); return t;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void applyCinema() {
        heading.setVisibility(cinema ? View.GONE : View.VISIBLE);
        footer.setVisibility(cinema ? View.GONE : View.VISIBLE);
        if (web != null) web.setFocusable(cinema || iframeControls);
    }
    private void revealControls() {
        cinema = false; iframeControls = false; applyCinema(); toggle.requestFocus();
    }
    private void showStatus(String message) { status.setText(message); }
    private void fail(String message) {
        nativeError = message; ready = false; keepAwake(false); revealControls();
        showStatus(message); retry.setVisibility(View.VISIBLE);
    }

    private void createPlayer() {
        if (isFinishing()) return;
        try {
            web = new WebView(this);
            web.setBackgroundColor(Color.BLACK);
            web.setFocusable(cinema || iframeControls);
            web.setContentDescription("Video. Center plays or pauses, left and right seek 10 seconds, down opens controls.");
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true); // Required by the official IFrame API.
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false); // Activity entered via explicit Watch action.
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
            web.setWebChromeClient(new WebChromeClient());
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return blockNavigation(request.getUrl());
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return blockNavigation(Uri.parse(url));
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (view == web && request.isForMainFrame()) fail("The embedded player could not load. Check your connection and Retry.");
                }
                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    if (view == web) { destroyPlayer(); fail("Android System WebView stopped. Select Retry to reload the player."); }
                    return true;
                }
            });
            viewport.addView(web, new FrameLayout.LayoutParams(-1,-1));
            String html;
            try (InputStream in = getAssets().open("player.html")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) bytes.write(buffer,0,n);
                html = bytes.toString("UTF-8");
            }
            // YouTube explicitly requires the app-ID origin for sideloaded apps too; not a spoofed website.
            String origin = "https://" + getPackageName();
            html = html.replace("__VIDEO_ID__", JSONObject.quote(videoId))
                .replace("__APP_ORIGIN__", JSONObject.quote(origin))
                .replace("__AUTO_START__", Boolean.toString(autoStart))
                .replace("__START_SECONDS__", Long.toString(Math.max(0, (long)position)));
            ready = false; playerState = -1; loadStarted = SystemClock.elapsedRealtime();
            showStatus("Loading YouTube…  ·  Down: controls  ·  Back: video details");
            web.loadDataWithBaseURL(origin + "/", html, "text/html", "UTF-8", null);
            if (cinema) web.requestFocus();
        } catch (Exception e) {
            destroyPlayer(); fail("In-app playback needs Android System WebView. Install or update it on this TV, then Retry.");
        }
    }

    private boolean blockNavigation(Uri uri) {
        String host = uri.getHost(), path = uri.getPath();
        if ("https".equals(uri.getScheme()) && ("www.youtube.com".equals(host) || "www.youtube-nocookie.com".equals(host))
                && path != null && path.startsWith("/embed/")) return false;
        showStatus("This link cannot open inside the player. Press Back for video details; no external app was opened.");
        return true;
    }

    private void command(String name) {
        if (web == null) return;
        boolean starts = name.equals("play") || (name.equals("toggle") && playerState != 1);
        if (starts && !active) return;
        web.evaluateJavascript("window.htCommand && htCommand(" + JSONObject.quote(name) + ")", null);
    }
    private void keepAwake(boolean playing) {
        if (active && playing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private String stateLabel() {
        switch (playerState) {
            case 1: return "Playing";
            case 2: return "Paused";
            case 3: return "Buffering";
            case 0: return "Finished";
            default: return "Ready · press Play";
        }
    }
    private void updateSession() {
        int state = playerState == 1 ? PlaybackState.STATE_PLAYING : playerState == 3 ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND)
            .setState(state, (long)(position*1000), playerState == 1 ? 1 : 0).build());
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (long)(duration*1000)).build());
        session.setActive(active && ready);
    }
    static String errorMessage(int error) {
        switch (error) {
            case 2: return "YouTube rejected this video ID (2).";
            case 5: return "YouTube cannot play this video in this TV's WebView (5). Try updating Android System WebView.";
            case 100: return "This video is unavailable, removed or private (100).";
            case 101: case 150: return "The owner does not allow embedded playback ("+error+"). Choose another video.";
            case 153: return "YouTube could not verify the embedded player identity (153). Update WebView or retry. No external app will open.";
            default: return "YouTube playback error " + error + ". Retry or choose another video.";
        }
    }
    private static String clock(double seconds) {
        long s = Math.max(0, (long)seconds);
        return s >= 3600 ? String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60)
            : String.format(Locale.ROOT,"%d:%02d",s/60,s%60);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean media = key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_MEDIA_PLAY
            || key == KeyEvent.KEYCODE_MEDIA_PAUSE || key == KeyEvent.KEYCODE_MEDIA_STOP
            || key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_MEDIA_REWIND;
        boolean surface = web != null && web.hasFocus() && !iframeControls;
        if (media || (surface && (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_DOWN))) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                switch (key) {
                    case KeyEvent.KEYCODE_MEDIA_PAUSE: case KeyEvent.KEYCODE_MEDIA_STOP: command("pause"); break;
                    case KeyEvent.KEYCODE_MEDIA_PLAY: command("play"); break;
                    case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND: command("rewind"); break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: command("forward"); break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: revealControls(); break;
                    default: command("toggle");
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if (iframeControls) { revealControls(); return; }
        finish();
    }
    private void startPolling() { handler.removeCallbacks(poll); if (active) handler.post(poll); }
    @Override protected void onStart() { super.onStart(); if (viewport != null && web == null) createPlayer(); }
    @Override protected void onResume() { super.onResume(); active = true; if (web != null) { web.onResume(); command("foreground"); startPolling(); } }
    @Override protected void onPause() {
        command("background"); active = false; autoStart = false; handler.removeCallbacks(poll); keepAwake(false);
        if (session != null) session.setActive(false);
        if (web != null) web.onPause();
        super.onPause();
    }
    @Override protected void onStop() { destroyPlayer(); super.onStop(); }
    private void destroyPlayer() {
        handler.removeCallbacks(poll); keepAwake(false);
        if (web != null) {
            WebView previous = web; web = null; viewport.removeView(previous);
            previous.stopLoading(); previous.destroy();
        }
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putDouble("position",position); out.putBoolean("cinema",cinema);
    }
    @Override protected void onDestroy() {
        destroyPlayer(); handler.removeCallbacksAndMessages(null);
        if (session != null) session.release();
        super.onDestroy();
    }
}
