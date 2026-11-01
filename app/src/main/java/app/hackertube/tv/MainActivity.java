package app.hackertube.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
    final ExecutorService workers=Executors.newSingleThreadExecutor();
    final ThreadPoolExecutor thumbnails=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    final Handler main=new Handler(Looper.getMainLooper());
    final Runnable searchChanged=()->apply(true);
    volatile int imageGeneration=0;
    String focusedVideo="";
    int restoreScroll=0;
    boolean restoreCardFocus=false;
    ScrollView catalogScroll;
    Button discoverButton, savedButton;
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
        if(state!=null){focusedVideo=state.getString("focusedVideo","");restoreScroll=state.getInt("scroll",0);restoreCardFocus=state.getBoolean("cardFocus",false);}
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        images.resize((am.isLowRamDevice()?4:12)*1024*1024);
        shell();
        workers.execute(()->{
            try(InputStream in=getAssets().open("catalog.json")) {
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                JSONArray all=new JSONArray(out.toString("UTF-8"));
                for(int i=0;i<all.length();i++){JSONObject v=all.getJSONObject(i);catalog.add(v);JSONArray t=v.getJSONArray("topics");for(int j=0;j<t.length();j++)topics.add(t.getString(j));}
                runOnUiThread(()->{if(isDestroyed())return;ready=true;apply(false);if(restoreCardFocus)focusVideo(focusedVideo);catalogScroll.post(()->catalogScroll.scrollTo(0,restoreScroll));});
            }catch(Exception e){runOnUiThread(()->count.setText("Could not load the bundled catalog. Please reinstall HackerTube."));}
        });
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("topic",topic);b.putString("mode",mode);b.putString("query",query);b.putInt("page",page);b.putString("focusedVideo",focusedVideo);b.putInt("scroll",catalogScroll.getScrollY());b.putBoolean("cardFocus",catalogScroll.hasFocus());}
    @Override public void onDestroy(){imageGeneration++;main.removeCallbacksAndMessages(null);workers.shutdownNow();thumbnails.shutdownNow();images.evictAll();super.onDestroy();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_UI_HIDDEN)images.evictAll();else if(level>=TRIM_MEMORY_RUNNING_LOW)images.trimToSize(images.maxSize()/2);}
    // Top-navigation TV pattern: grid -> active tab -> Discover -> system Home. No exit gate.
    @Override public void onBackPressed(){
        if(catalogScroll.hasFocus()){catalogScroll.scrollTo(0,0);activeTab().requestFocus();return;}
        if(!mode.equals("Discover")||!topic.equals("All topics")||!query.isEmpty()||page!=0){mode="Discover";topic="All topics";query="";search.setText("");main.removeCallbacks(searchChanged);apply(true);discoverButton.requestFocus();return;}
        super.onBackPressed();
    }
    Button activeTab(){return mode.equals("Saved")?savedButton:discoverButton;}
    void focusVideo(String id){View target=grid.findViewWithTag(id);if(target==null&&grid.getChildCount()>0&&grid.getChildAt(0) instanceof LinearLayout)target=((LinearLayout)grid.getChildAt(0)).getChildAt(0);if(target!=null&&target.isFocusable())target.requestFocus();else activeTab().requestFocus();}
    int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable bg(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));if(stroke!=0)d.setStroke(dp(3),stroke);return d;}
    void paintFocus(View v){v.setBackground(bg(v.hasFocus()?Color.rgb(49,49,47):v.isSelected()?Color.rgb(53,39,31):PANEL,v.hasFocus()?ORANGE:0));v.setElevation(v.hasFocus()?dp(8):0);}
    void focusable(View v){v.setFocusable(true);paintFocus(v);v.setOnFocusChangeListener((w,on)->{paintFocus(w);if(on&&w.getTag() instanceof String)focusedVideo=(String)w.getTag();});}
    Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setTextColor(INK);b.setTextSize(14);b.setAllCaps(false);b.setPadding(dp(16),dp(8),dp(16),dp(8));focusable(b);b.setOnClickListener(v->action.run());return b;}
    void addButton(LinearLayout row,Button b){b.setMinHeight(dp(48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(0,0,dp(10),0);row.addView(b,p);}
    void shell(){
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(48),dp(27),dp(48),dp(27));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("▶  hacker",27,INK);header.addView(brand);header.addView(text("tube",27,ORANGE));
        TextView tagline=text("   SIGNAL OVER NOISE",12,MUTED);header.addView(tagline,new LinearLayout.LayoutParams(0,-2,1));
        addButton(header,button("About",this::about));root.addView(header);
        TextView headline=text("Worth coming back to.",24,INK);headline.setPadding(0,dp(8),0,dp(3));if(Build.VERSION.SDK_INT>=28)headline.setAccessibilityHeading(true);root.addView(headline);
        nav=new LinearLayout(this);nav.setPadding(0,dp(16),0,dp(12));root.addView(nav);
        discoverButton=button("Discover",()->{mode="Discover";apply(true);});addButton(nav,discoverButton);
        savedButton=button("Saved",()->{mode="Saved";apply(true);});addButton(nav,savedButton);
        addButton(nav,button("Topics ▾",this::chooseTopic));
        search=new EditText(this);search.setSingleLine();search.setTextSize(15);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("Search titles, channels, ideas");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setText(query);
        search.setContentDescription("Search videos by title, channel or topic");search.setMinHeight(dp(48));nav.addView(search,new LinearLayout.LayoutParams(0,-2,1));
        search.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH)return false;main.removeCallbacks(searchChanged);apply(true);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);focusVideo("");return true;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){query=s.toString();main.removeCallbacks(searchChanged);main.postDelayed(searchChanged,250);}public void afterTextChanged(Editable e){}});
        count=text("Loading curated catalog…",14,MUTED);count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(count);
        catalogScroll=new ScrollView(this);catalogScroll.setFillViewport(true);grid=column();grid.setPadding(0,dp(12),0,dp(16));catalogScroll.addView(grid);root.addView(catalogScroll,new LinearLayout.LayoutParams(-1,0,1));
        activeTab().requestFocus();
    }
    void chooseTopic(){ArrayList<String> options=new ArrayList<>();options.add("All topics");options.addAll(topics);new AlertDialog.Builder(this).setTitle("Explore a topic").setSingleChoiceItems(options.toArray(new String[0]),options.indexOf(topic),(d,i)->{topic=options.get(i);apply(true);d.dismiss();}).show();}
    void apply(boolean reset){
        if(!ready)return;if(reset){page=0;catalogScroll.scrollTo(0,0);}filtered.clear();String q=query.trim().toLowerCase(Locale.ROOT);
        for(JSONObject v:catalog){if(mode.equals("Saved")&&!saved.contains(v.optString("id")))continue;if(!topic.equals("All topics")&&!hasTopic(v,topic))continue;
            String hay=v.optString("title")+" "+v.optString("channel")+" "+v.optString("summary")+" "+v.optJSONArray("topics");
            if(hay.toLowerCase(Locale.ROOT).contains(q))filtered.add(v);
        }
        page=Math.min(page,Math.max(0,(filtered.size()-1)/PAGE_SIZE));render();
    }
    boolean hasTopic(JSONObject v,String topic){JSONArray a=v.optJSONArray("topics");for(int i=0;i<a.length();i++)if(topic.equals(a.optString(i)))return true;return false;}
    void render(){
        imageGeneration++;thumbnails.getQueue().clear();grid.removeAllViews();
        discoverButton.setSelected(mode.equals("Discover"));savedButton.setSelected(mode.equals("Saved"));paintFocus(discoverButton);paintFocus(savedButton);
        count.setText(mode+"  /  "+topic+"  ·  "+filtered.size()+" videos");
        if(filtered.isEmpty()){grid.addView(text(mode.equals("Saved")?"No saved videos match. Save a video from its detail page.":"No matches. Try another search or topic.",20,INK));return;}
        int end=Math.min(filtered.size(),(page+1)*PAGE_SIZE);LinearLayout row=null;
        for(int i=page*PAGE_SIZE;i<end;i++){
            if(i%3==0){row=new LinearLayout(this);grid.addView(row);}
            JSONObject v=filtered.get(i);LinearLayout card=column();card.setTag(v.optString("id"));card.setPadding(dp(8),dp(8),dp(8),dp(10));focusable(card);
            card.setContentDescription(v.optString("title")+", by "+v.optString("channel")+", "+v.optInt("recommenders")+" recommenders, "+v.optString("first")+" to "+v.optString("last")+". Open video details");card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);card.setOnClickListener(w->details(v,card));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(4),dp(4),dp(4),dp(12));row.addView(card,p);
            ImageView image=new ImageView(this){@Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);super.onMeasure(w,MeasureSpec.makeMeasureSpec(width*9/16,MeasureSpec.EXACTLY));}};
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.rgb(38,43,53));card.addView(image,new LinearLayout.LayoutParams(-1,-2));thumbnail(v.optString("id"),image);
            TextView title=text(v.optString("title"),16,INK);title.setLines(2);title.setEllipsize(TextUtils.TruncateAt.END);title.setPadding(0,dp(7),0,0);card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView channel=text(v.optString("channel"),14,MUTED);channel.setSingleLine();channel.setEllipsize(TextUtils.TruncateAt.END);card.addView(channel);
            TextView stats=text(v.optInt("recommenders")+" recommenders · "+v.optString("first")+"–"+v.optString("last"),14,ORANGE);stats.setMaxLines(2);card.addView(stats);
            // Expose each card as one descriptive action, not duplicated thumbnail/title nodes.
            for(int child=0;child<card.getChildCount();child++)card.getChildAt(child).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if(row!=null)for(int i=0;i<(3-(end-page*PAGE_SIZE)%3)%3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        LinearLayout pages=new LinearLayout(this);pages.setGravity(Gravity.CENTER);grid.addView(pages);
        if(page>0)addButton(pages,button("← Previous",()->{page--;render();catalogScroll.scrollTo(0,0);focusVideo("");}));
        TextView label=text("  Page "+(page+1)+" / "+((filtered.size()+PAGE_SIZE-1)/PAGE_SIZE)+"  ",14,MUTED);pages.addView(label);
        if(end<filtered.size())addButton(pages,button("Next →",()->{page++;render();catalogScroll.scrollTo(0,0);focusVideo("");}));
    }
    void thumbnail(String id,ImageView view){
        Bitmap cached=images.get(id);if(cached!=null){view.setImageBitmap(cached);return;}
        final int generation=imageGeneration;
        thumbnails.execute(()->{if(generation!=imageGeneration||Thread.currentThread().isInterrupted())return;HttpURLConnection c=null;try{
            c=(HttpURLConnection)new URL("https://i.ytimg.com/vi/"+id+"/mqdefault.jpg").openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);
            try(InputStream in=c.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null&&generation==imageGeneration){images.put(id,b);runOnUiThread(()->{if(!isDestroyed()&&generation==imageGeneration)view.setImageBitmap(b);});}}
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
        ScrollView scroll=new ScrollView(this);scroll.addView(body);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(v.optString("title")).setView(scroll).create();
        dialog.setOnDismissListener(d->{if(mode.equals("Saved")){apply(false);focusVideo(v.optString("id"));}else origin.requestFocus();});dialog.show();dialog.getWindow().setLayout(Math.min(dp(800),getResources().getDisplayMetrics().widthPixels-dp(96)),getResources().getDisplayMetrics().heightPixels-dp(54));actions.getChildAt(0).requestFocus();
    }
    void watch(String id){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+id));i.setPackage("com.google.android.youtube.tv");
        try{startActivity(i);}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setTitle("YouTube for TV is required").setMessage("Install or enable the official YouTube app on this TV, then try again. Your selected video is:\n\nhttps://www.youtube.com/watch?v="+id).setPositiveButton("OK",null).show();}
    }
    // TV-WB: never launch a browser. Evidence is native; original links can be read on another device.
    void openUrl(String url){new AlertDialog.Builder(this).setTitle("Read the original on your phone or computer").setMessage("The recommendation is shown in HackerTube. For the original HN discussion, open:\n\n"+url).setPositiveButton("Done",null).show();}
    void about(){new AlertDialog.Builder(this).setTitle("HackerTube · TV edition").setMessage("A quiet catalog, not an engagement feed.\n\n"+catalog.size()+" videos with at least 3 distinct recommenders in 3 distinct months across 2 or more calendar years. News is excluded. Ranked by the source recommendation score.\n\nData: HackerTube (hackertube.org), export 2026-10-31, and open-index/hacker-news (huggingface.co/datasets/open-index/hacker-news), under Open Data Commons Attribution License v1.0 (opendatacommons.org/licenses/by/1-0/). Derived catalog filtered from that export. HN data is provided by Y Combinator; comments belong to their authors.\n\nLabels and summaries are AI-generated and may be wrong. Evidence is capped at 20 source mentions per video, so this is conservative, not exhaustive. Year spans are calendar years, not necessarily 24 months.\n\nYouTube titles and thumbnails belong to their creators. Not affiliated with Karpathy, YouTube, Google, Hacker News or Y Combinator. Saved videos stay on this device. Thumbnails contact YouTube; playback opens YouTube.").setPositiveButton("Done",null).show();}
}
