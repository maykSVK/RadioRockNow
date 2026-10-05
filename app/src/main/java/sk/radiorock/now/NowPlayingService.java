package sk.radiorock.now;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;

/**
 * Mediálna služba pre Android Auto. Nič neprehráva – iba zobrazuje na karte médií
 * aktuálnu skladbu Rádia ROCK, obal albumu a strieda interpreta s krátkymi zaujímavosťami.
 * Dlhé texty sa posúvajú ako bežiaci text.
 */
public class NowPlayingService extends MediaBrowserService implements NowPlayingMonitor.Listener {
    /** Počet znakov, ktoré sa vojdú na kartu v aute. */
    private static final int WIDTH = 22;
    private static final long TICK_MS = 700;
    private static final int STEP = 2;          // znakov na tik pri posúvaní
    private static final int STATIC_TICKS = 11; // ~8 s pre krátky text
    private static final int PAUSE_TICKS = 3;   // pauza na začiatku posunu
    private static final String GAP = "   •   ";

    private MediaSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TrackInfo track;
    private int factIdx = 0;
    private int factTicks = 0;
    private int titleTicks = 0;

    private TrackInfo artFor;
    private Bitmap artBitmap;
    private Bitmap fallbackArt;

    private String lastTitle, lastLine;
    private Bitmap lastArt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (track != null) {
                titleTicks++;
                factTicks++;
                if (factTicks >= cycleTicks(currentFact())) {
                    factIdx++;
                    factTicks = 0;
                }
                publish();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "RadioRockNow");
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setActions(0)
                .build());
        fallbackArt = makeFallbackArt();
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Rádio ROCK")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Čakám na skladbu…")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, fallbackArt)
                .build());
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        NowPlayingMonitor.get().acquire(this);
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        NowPlayingMonitor.get().release(this);
        session.setActive(false);
        session.release();
        super.onDestroy();
    }

    @Override
    public void onTrackChanged(TrackInfo t) {
        handler.post(() -> {
            if (track != t) {
                factIdx = 0;
                factTicks = 0;
                titleTicks = 0;
            }
            track = t;
            publish();
        });
    }

    private String currentFact() {
        List<String> lines = new ArrayList<>();
        lines.add(track.artist);
        lines.addAll(track.shortFacts);
        return lines.get(factIdx % lines.size());
    }

    private static int cycleTicks(String text) {
        if (text.length() <= WIDTH) return STATIC_TICKS;
        return PAUSE_TICKS + (text.length() + GAP.length()) / STEP + 2;
    }

    /** Okno textu: krátky text sa nemení, dlhý sa posúva po znakoch. */
    private static String window(String text, int ticks) {
        if (text.length() <= WIDTH) return text;
        String loop = text + GAP;
        int off = (Math.max(0, ticks - PAUSE_TICKS) * STEP) % loop.length();
        String doubled = loop + loop;
        return doubled.substring(off, off + WIDTH);
    }

    private Bitmap art() {
        if (artFor != track) {
            artFor = track;
            artBitmap = null;
        }
        byte[] b = track.artBytes;
        if (artBitmap == null && b != null) {
            artBitmap = BitmapFactory.decodeByteArray(b, 0, b.length);
        }
        return artBitmap != null ? artBitmap : fallbackArt;
    }

    private void publish() {
        if (track == null) return;
        String title = window(track.song, titleTicks);
        String line = window(currentFact(), factTicks);
        Bitmap art = art();
        if (title.equals(lastTitle) && line.equals(lastLine) && art == lastArt) return;
        lastTitle = title;
        lastLine = line;
        lastArt = art;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, line)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Rádio ROCK")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, line)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
                .build());
    }

    private Bitmap makeFallbackArt() {
        Bitmap bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        android.graphics.drawable.Drawable d = getDrawable(R.drawable.ic_launcher);
        d.setBounds(0, 0, 256, 256);
        d.draw(c);
        return bmp;
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        result.sendResult(new ArrayList<>());
    }
}
