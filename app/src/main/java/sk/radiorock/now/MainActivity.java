package sk.radiorock.now;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity implements NowPlayingMonitor.Listener {
    private TextView songView;
    private TextView artistView;
    private TextView infoView;

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
        root.addView(head);
        root.addView(songView);
        root.addView(artistView);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
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
            songView.setText(t.song);
            artistView.setText(t.artist);
            infoView.setText(t.longText);
        });
    }
}
