package sk.radiorock.now;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.MediaDescription;
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
 * Mediálna služba pre Android Auto. Nič neprehráva – iba zobrazuje aktuálnu skladbu Rádia ROCK.
 * <ul>
 *   <li>Malá karta: názov skladby, interpret, obal (dlhé texty sa posúvajú).</li>
 *   <li>Veľká obrazovka: zaujímavosti v poli „album" a v zozname (prehliadač médií).</li>
 * </ul>
 */
public class NowPlayingService extends MediaBrowserService implements NowPlayingMonitor.Listener {
    /** Počet znakov, ktoré sa vojdú na malú kartu v aute. */
    private static final int WIDTH = 22;
    private static final long TICK_MS = 700;
    private static final int STEP = 2;          // znakov na tik pri posúvaní
    private static final int PAUSE_TICKS = 3;   // pauza na začiatku posunu
    private static final String GAP = "   •   ";
    private static final String ROOT = "root";

    private MediaSession session;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TrackInfo track;
    private int ticks = 0;

    private TrackInfo artFor;
    private Bitmap artBitmap;
    private Bitmap fallbackArt;

    private String lastTitle, lastArtist, lastAlbum;
    private Bitmap lastArt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (track != null) {
                ticks++;
                publish();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "RadioRockNow");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onCustomAction(String action, Bundle extras) {
                if ("REFRESH".equals(action)) {
                    NowPlayingMonitor.get().refresh();
                }
            }
        });
        PlaybackState.CustomAction refreshAction = new PlaybackState.CustomAction.Builder(
                "REFRESH", "Obnoviť", android.R.drawable.ic_popup_sync).build();
        session.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setActions(0)
                .addCustomAction(refreshAction)
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
            if (track != t) ticks = 0;
            track = t;
            publish();
            notifyChildrenChanged(ROOT);
        });
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
        String title = window(track.song, ticks);
        String artist = window(track.artist, ticks);
        String facts = track.factsLine();
        String album = facts.isEmpty() ? "Rádio ROCK" : facts;
        Bitmap art = art();
        if (title.equals(lastTitle) && artist.equals(lastArtist) && album.equals(lastAlbum) && art == lastArt) return;
        lastTitle = title;
        lastArtist = artist;
        lastAlbum = album;
        lastArt = art;
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, -1)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art);
        if (track.wikiText != null) {
            b.putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, track.wikiText);
        }
        session.setMetadata(b.build());
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
        return new BrowserRoot(ROOT, null);
    }

    /** Zoznam zaujímavostí o aktuálnej skladbe (zobrazí sa po otvorení aplikácie v aute). */
    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        TrackInfo t = track != null ? track : NowPlayingMonitor.get().current();
        if (t == null) {
            items.add(item("wait", "Čakám na skladbu…", ""));
        } else {
            int i = 0;
            for (String[] row : t.detailItems()) {
                items.add(item("d" + (i++), row[0], row[1]));
            }
        }
        result.sendResult(items);
    }

    private static MediaBrowser.MediaItem item(String id, String title, String subtitle) {
        MediaDescription d = new MediaDescription.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle.isEmpty() ? null : subtitle)
                .build();
        return new MediaBrowser.MediaItem(d, MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }
}
