package sk.radiorock.now;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.widget.ProgressBar;
import android.view.View;

public class MainActivity extends Activity implements NowPlayingMonitor.Listener {
    private TextView songView;
    private TextView artistView;
    private TextView infoView;
    private SwipeRefreshLayout swipe;
    private ProgressBar loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#050607"));
        int p = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());
        root.setPadding(p, p * 2, p, p);

        TextView head = text("RÁDIO ROCK – práve hrá", 12, "#CD1719", true);
        songView = text("Čakám na skladbu…", 26, "#F9F0DF", true);
        artistView = text("", 18, "#F9F0DF", false);
        infoView = text("", 15, "#CCCCCC", false);
        infoView.setPadding(0, p, 0, 0);
        TextView hint = text("Tip: v Android Auto otvor aplikáciu Rádio ROCK Now – karta médií zobrazí skladbu a zaujímavosti.", 12, "#888888", false);
        hint.setGravity(Gravity.START);

        ScrollView sv = new ScrollView(this);
        sv.addView(infoView);
        
        swipe = new SwipeRefreshLayout(this);
        swipe.addView(sv);
        swipe.setOnRefreshListener(() -> {
            NowPlayingMonitor.get().refresh();
        });

        loader = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loader.setIndeterminate(true);
        loader.setVisibility(View.GONE);

        root.addView(head);
        root.addView(songView);
        root.addView(artistView);
        root.addView(loader, new LinearLayout.LayoutParams(-1, -2));
        root.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(hint);
        setContentView(root);
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        return tv;
    }

    @Override
    protected void onStart() {
        super.onStart();
        NowPlayingMonitor.get().acquire(this);
    }

    @Override
    protected void onStop() {
        NowPlayingMonitor.get().release(this);
        super.onStop();
    }

    @Override
    public void onTrackChanged(TrackInfo t) {
        runOnUiThread(() -> {
            if (swipe != null) swipe.setRefreshing(false);
            if (loader != null) loader.setVisibility(t.triviaDone ? View.GONE : View.VISIBLE);
            songView.setText(t.song);
            artistView.setText(t.artist);
            StringBuilder sb = new StringBuilder();
            if (!t.shortFacts.isEmpty()) {
                for (String f : t.shortFacts) sb.append("• ").append(f).append('\n');
                sb.append('\n');
            }
            if (!t.longText.isEmpty()) sb.append(t.longText).append("\n\n");
            if (!t.triviaDone) sb.append("Načítavam zaujímavosti…");
            else if (t.longText.isEmpty() && t.shortFacts.isEmpty()) sb.append("Zaujímavosti sa nenašli.");
            if (t.triviaDone && !t.debug.isEmpty()) {
                sb.append("\n\nDiagnostika:\n");
                for (String d : t.debug) sb.append("– ").append(d).append('\n');
            }
            infoView.setText(sb.toString());
        });
    }
}
