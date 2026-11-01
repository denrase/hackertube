package app.hackertube.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.text.*;
import android.util.LruCache;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

/** Native, dependency-free TV catalog. Playback is delegated to the official YouTube app. */
public class MainActivity extends Activity {
    final int BG=Color.rgb(14,17,23), PANEL=Color.rgb(27,32,42), INK=Color.rgb(241,242,246), MUTED=Color.rgb(161,172,190), ORANGE=Color.rgb(255,119,48);
    final ExecutorService workers=Executors.newFixedThreadPool(4);
    final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(16*1024*1024) {
        protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    final ArrayList<JSONObject> catalog=new ArrayList<>(), filtered=new ArrayList<>();
    final TreeSet<String> topics=new TreeSet<>();
    Set<String> saved;
    LinearLayout root, grid, nav;
    TextView count;
    EditText search;
    String topic="All topics", mode="Discover", query="";
    int page=0;
    static final int PAGE_SIZE=24;
    boolean ready=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        saved=new HashSet<>(getPreferences(0).getStringSet("saved", Collections.emptySet()));
        if(state!=null){topic=state.getString("topic","All topics"); mode=state.getString("mode","Discover"); query=state.getString("query",""); page=state.getInt("page",0);}
        shell();
        workers.execute(()->{
            try(InputStream in=getAssets().open("catalog.json")) {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                JSONArray all=new JSONArray(out.toString("UTF-8"));
                for(int i=0;i<all.length();i++){JSONObject v=all.getJSONObject(i);catalog.add(v);JSONArray t=v.getJSONArray("topics");for(int j=0;j<t.length();j++)topics.add(t.getString(j));}
                runOnUiThread(()->{ready=true; apply(false);});
            }catch(Exception e){runOnUiThread(()->count.setText("Could not load the bundled catalog. Please reinstall HackerTube."));}
        });
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("topic",topic);b.putString("mode",mode);b.putString("query",query);b.putInt("page",page);}
    @Override public void onDestroy(){workers.shutdownNow();super.onDestroy();}
    int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable bg(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));if(stroke!=0)d.setStroke(dp(3),stroke);return d;}
    void focusable(View v){v.setFocusable(true);v.setBackground(bg(PANEL,0));v.setOnFocusChangeListener((w,on)->{w.setBackground(bg(on?Color.rgb(49,49,47):PANEL,on?ORANGE:0));w.setElevation(on?dp(8):0);});}
    Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextColor(INK);b.setTextSize(14);b.setAllCaps(false);b.setPadding(dp(16),dp(8),dp(16),dp(8));focusable(b);b.setOnClickListener(v->action.run());return b;}
    void addButton(LinearLayout row,Button b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(48));p.setMargins(0,0,dp(10),0);row.addView(b,p);}
    void shell(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(36),dp(22),dp(36),dp(12));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("▶  hacker",27,INK);header.addView(brand);header.addView(text("tube",27,ORANGE));
        TextView tagline=text("   SIGNAL OVER NOISE",12,MUTED);header.addView(tagline,new LinearLayout.LayoutParams(0,-2,1));
        addButton(header,button("About",this::about));root.addView(header);
        TextView headline=text("Worth coming back to.",30,INK);headline.setPadding(0,dp(14),0,dp(3));root.addView(headline);
        root.addView(text("Videos recommended by HN readers, across multiple years.",15,MUTED));
        nav=new LinearLayout(this);nav.setPadding(0,dp(16),0,dp(12));root.addView(nav);
        addButton(nav,button("Discover",()->{mode="Discover";apply(true);}));
        addButton(nav,button("Saved",()->{mode="Saved";apply(true);}));
        addButton(nav,button("Topics ▾",this::chooseTopic));
        search=new EditText(this);search.setSingleLine();search.setTextSize(15);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("Search titles, channels, ideas");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(query);
        nav.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString();apply(true);}public void afterTextChanged(Editable e){}});
        count=text("Loading curated catalog…",13,MUTED);root.addView(count);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);grid=column();grid.setPadding(0,dp(12),0,dp(16));scroll.addView(grid);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    void chooseTopic(){ArrayList<String> options=new ArrayList<>();options.add("All topics");options.addAll(topics);new AlertDialog.Builder(this).setTitle("Explore a topic").setItems(options.toArray(new String[0]),(d,i)->{topic=options.get(i);apply(true);}).show();}
    void apply(boolean reset){
        if(!ready)return;if(reset)page=0;filtered.clear();String q=query.trim().toLowerCase(Locale.ROOT);
        for(JSONObject v:catalog){if(mode.equals("Saved")&&!saved.contains(v.optString("id")))continue;if(!topic.equals("All topics")&&!hasTopic(v,topic))continue;
            String hay=v.optString("title")+" "+v.optString("channel")+" "+v.optString("summary")+" "+v.optJSONArray("topics");
            if(hay.toLowerCase(Locale.ROOT).contains(q))filtered.add(v);
        }
        page=Math.min(page,Math.max(0,(filtered.size()-1)/PAGE_SIZE));render();
    }
    boolean hasTopic(JSONObject v,String topic){JSONArray a=v.optJSONArray("topics");for(int i=0;i<a.length();i++)if(topic.equals(a.optString(i)))return true;return false;}
    void render(){
        grid.removeAllViews();count.setText(mode+"  /  "+topic+"  ·  "+filtered.size()+" videos  ·  Ranked by repeat recommendations");
        if(filtered.isEmpty()){grid.addView(text(mode.equals("Saved")?"No saved videos match. Save a video from its detail page.":"No matches. Try another search or topic.",20,INK));return;}
        int end=Math.min(filtered.size(),(page+1)*PAGE_SIZE);LinearLayout row=null;
        for(int i=page*PAGE_SIZE;i<end;i++){
            if(i%3==0){row=new LinearLayout(this);grid.addView(row);}
            JSONObject v=filtered.get(i);LinearLayout card=column();card.setPadding(dp(8),dp(8),dp(8),dp(10));focusable(card);card.setContentDescription(v.optString("title")+", "+v.optInt("recommenders")+" recommenders");card.setOnClickListener(w->details(v,card));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(230),1);p.setMargins(dp(4),dp(4),dp(4),dp(8));row.addView(card,p);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(Color.rgb(38,43,53));card.addView(image,new LinearLayout.LayoutParams(-1,dp(125)));thumbnail(v.optString("id"),image);
            TextView title=text(v.optString("title"),16,INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(7),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,0,1));
            TextView channel=text(v.optString("channel"),12,MUTED);channel.setSingleLine();channel.setEllipsize(TextUtils.TruncateAt.END);card.addView(channel);
            card.addView(text(v.optInt("recommenders")+" recommenders · "+v.optString("first")+"–"+v.optString("last"),12,ORANGE));
        }
        if(row!=null)for(int i=0;i<(3-(end-page*PAGE_SIZE)%3)%3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        LinearLayout pages=new LinearLayout(this);pages.setGravity(Gravity.CENTER);grid.addView(pages);
        if(page>0)addButton(pages,button("← Previous",()->{page--;render();grid.getChildAt(0).requestFocus(View.FOCUS_DOWN);}));
        TextView label=text("  Page "+(page+1)+" / "+((filtered.size()+PAGE_SIZE-1)/PAGE_SIZE)+"  ",14,MUTED);pages.addView(label);
        if(end<filtered.size())addButton(pages,button("Next →",()->{page++;render();grid.getChildAt(0).requestFocus(View.FOCUS_DOWN);}));
    }
    void thumbnail(String id,ImageView view){
        Bitmap cached=images.get(id);if(cached!=null){view.setImageBitmap(cached);return;}
        workers.execute(()->{HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL("https://i.ytimg.com/vi/"+id+"/mqdefault.jpg").openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);
            try(InputStream in=c.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null){images.put(id,b);runOnUiThread(()->{if(!isDestroyed())view.setImageBitmap(b);});}}
        }catch(IOException ignored){}finally{if(c!=null)c.disconnect();}});
    }
    void details(JSONObject v,View origin){
        LinearLayout body=column();body.setPadding(dp(24),dp(12),dp(24),dp(20));
        body.addView(text(v.optString("channel")+" · "+v.optInt("recommenders")+" recommenders · "+v.optInt("months")+" distinct months",15,ORANGE));
        TextView summary=text(v.optString("summary")+"\n\nWHY HN RECOMMENDS IT\n"+v.optString("why"),18,INK);summary.setPadding(0,dp(16),0,dp(16));body.addView(summary);
        LinearLayout actions=new LinearLayout(this);body.addView(actions);
        addButton(actions,button("▶ Watch on YouTube",()->watch(v.optString("id"))));
        Button save=button(saved.contains(v.optString("id"))?"Unsave":"Save for later",()->{});
        save.setOnClickListener(w->{String id=v.optString("id");if(!saved.add(id))saved.remove(id);getPreferences(0).edit().putStringSet("saved",new HashSet<>(saved)).apply();save.setText(saved.contains(id)?"Unsave":"Save for later");});addButton(actions,save);
        TextView note=text("Playback opens the official YouTube app. Press Back to return. Availability and ads are controlled by YouTube.\n\nRECOMMENDATION EVIDENCE",13,MUTED);note.setPadding(0,dp(16),0,dp(10));body.addView(note);
        JSONArray evidence=v.optJSONArray("evidence");
        for(int i=0;i<evidence.length();i++){JSONObject e=evidence.optJSONObject(i);TextView quote=text(e.optString("month")+" · "+e.optString("author")+"\n“"+e.optString("quote")+"”",16,INK);quote.setPadding(0,dp(12),0,dp(8));body.addView(quote);addButton(body,button("HN comment #"+e.optLong("id"),()->openUrl("https://news.ycombinator.com/item?id="+e.optLong("id"))));}
        ScrollView scroll=new ScrollView(this);scroll.addView(body);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(v.optString("title")).setView(scroll).setNegativeButton("Back",null).create();
        dialog.setOnDismissListener(d->{if(mode.equals("Saved"))apply(false);else origin.requestFocus();});dialog.show();dialog.getWindow().setLayout(dp(800),getResources().getDisplayMetrics().heightPixels-dp(64));actions.getChildAt(0).requestFocus();
    }
    void watch(String id){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+id));i.setPackage("com.google.android.youtube.tv");
        try{startActivity(i);}catch(ActivityNotFoundException e){openUrl("https://www.youtube.com/watch?v="+id);}
    }
    void openUrl(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("No compatible app installed").setMessage("Open this link on your phone or install a compatible app:\n\n"+url).setPositiveButton("OK",null).show();}}
    void about(){new AlertDialog.Builder(this).setTitle("HackerTube · TV edition").setMessage("A quiet catalog, not an engagement feed.\n\n"+catalog.size()+" videos with at least 3 distinct recommenders in 3 distinct months across 2 or more calendar years. News is excluded. Ranked by the source recommendation score.\n\nData: HackerTube (hackertube.org), export 2026-10-31, and open-index/hacker-news (huggingface.co/datasets/open-index/hacker-news), under Open Data Commons Attribution License v1.0 (opendatacommons.org/licenses/by/1-0/). Derived catalog filtered from that export. HN data is provided by Y Combinator; comments belong to their authors.\n\nLabels and summaries are AI-generated and may be wrong. Evidence is capped at 20 source mentions per video, so this is conservative, not exhaustive. Year spans are calendar years, not necessarily 24 months.\n\nYouTube titles and thumbnails belong to their creators. Not affiliated with Karpathy, YouTube, Google, Hacker News or Y Combinator. Saved videos stay on this device. Thumbnails contact YouTube; playback opens YouTube.").setPositiveButton("Done",null).show();}
}
