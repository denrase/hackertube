package app.hackertube.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runtime-only metadata: no YouTube API metadata is redistributed in the bundled catalog. */
final class DurationMetadata {
    private static final long TTL = 30L * 24 * 60 * 60 * 1000;
    private final SharedPreferences cache;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;

    DurationMetadata(Context context) { cache = context.getSharedPreferences("video_durations", Context.MODE_PRIVATE); }
    long get(String id) {
        if (System.currentTimeMillis() - cache.getLong(id + ".at", 0) >= TTL) return 0;
        return cache.getLong(id + ".seconds", 0);
    }
    static String format(long seconds) {
        if (seconds <= 0) return "—";
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60)
            : String.format(Locale.ROOT, "%d:%02d", seconds/60, seconds%60);
    }
    static void recordPlayback(Context context, String id, double seconds) {
        if ((Double.isNaN(seconds) || Double.isInfinite(seconds)) || seconds <= 0) return;
        SharedPreferences prefs=context.getSharedPreferences("video_durations", Context.MODE_PRIVATE);
        if(prefs.getLong(id+".seconds",0)>0 && System.currentTimeMillis()-prefs.getLong(id+".at",0)<TTL)return;
        prefs.edit().putLong(id + ".seconds", Math.round(seconds)).putLong(id + ".at", System.currentTimeMillis()).apply();
    }
    void fetch(List<String> ids, Runnable updated) {
        if (closed) return;
        ArrayList<String> missing = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : ids) {
            if (id.matches("[A-Za-z0-9_-]{11}") && get(id) <= 0 && now-cache.getLong(id+".attempt",0)>300000) missing.add(id);
        }
        if (missing.isEmpty()) { updated.run(); return; }
        worker.execute(() -> {
            HttpURLConnection connection = null;
            try {
                SharedPreferences.Editor attempted = cache.edit();
                for (String id : missing) attempted.putLong(id+".attempt",System.currentTimeMillis());
                attempted.apply();
                // Public metadata endpoint already used by HackerTube's own saved-video UI.
                String joined = android.text.TextUtils.join(",", missing);
                connection = (HttpURLConnection)new URL("https://hackertube.org/api/docs?vids="+joined).openConnection();
                connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "HackerTubeTV/0.4 (personal catalog client)");
                try (InputStream in=connection.getInputStream()) {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
                    while ((n=in.read(buffer))!=-1) {
                        bytes.write(buffer,0,n);
                        if (bytes.size()>2*1024*1024) throw new IllegalStateException("Metadata response too large");
                    }
                    JSONArray rows=new JSONArray(bytes.toString("UTF-8"));
                    SharedPreferences.Editor editor=cache.edit();
                    for(int i=0;i<rows.length();i++) {
                        JSONObject row=rows.getJSONObject(i);String id=row.optString("vid");long seconds=row.optLong("duration_s");
                        if(missing.contains(id)&&seconds>0) editor.putLong(id+".seconds",seconds).putLong(id+".at",System.currentTimeMillis());
                    }
                    editor.apply();
                }
            } catch (Exception ignored) {
                // Offline/missing metadata is not a catalog failure. Never invent a duration.
            } finally {
                if(connection!=null) connection.disconnect();
                if(!closed) main.post(()->{if(!closed)updated.run();});
            }
        });
    }
    void close() { closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null); }
}
