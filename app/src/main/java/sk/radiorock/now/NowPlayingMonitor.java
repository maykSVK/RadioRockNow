package sk.radiorock.now;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Zdieľaný monitor: číta ICY metadáta, kým ho niekto (auto / aplikácia) používa. */
final class NowPlayingMonitor implements IcyReader.Listener {
    interface Listener {
        void onTrackChanged(TrackInfo track);
    }

    private static final NowPlayingMonitor INSTANCE = new NowPlayingMonitor();

    static NowPlayingMonitor get() {
        return INSTANCE;
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IcyReader reader;
    private int users = 0;
    private String lastRaw = null;
    private volatile TrackInfo current;

    TrackInfo current() {
        return current;
    }

    synchronized void acquire(Listener l) {
        listeners.add(l);
        if (users++ == 0) {
            reader = new IcyReader(this);
            reader.start();
        }
        if (current != null) l.onTrackChanged(current);
    }

    synchronized void release(Listener l) {
        listeners.remove(l);
        if (--users <= 0) {
            users = 0;
            if (reader != null) reader.stop();
            reader = null;
            lastRaw = null;
        }
    }

    @Override
    public void onStreamTitle(String raw) {
        synchronized (this) {
            if (raw.equals(lastRaw)) return;
            lastRaw = raw;
        }
        String song = raw;
        String artist = "Rádio ROCK";
        int i = raw.lastIndexOf(" - ");
        if (i > 0) {
            song = raw.substring(0, i).trim();
            artist = raw.substring(i + 3).trim();
        }
        final TrackInfo t = new TrackInfo(song, artist);
        if (i <= 0) t.triviaDone = true;
        current = t;
        notifyAllListeners(t);
        if (i > 0) {
            executor.execute(() -> {
                try {
                    TriviaRepository.fill(t);
                } catch (Throwable e) {
                    t.log("Chyba: " + e);
                } finally {
                    t.triviaDone = true;
                }
                if (current == t) notifyAllListeners(t);
            });
        }
    }

    private void notifyAllListeners(TrackInfo t) {
        for (Listener l : listeners) l.onTrackChanged(t);
    }

    synchronized void refresh() {
        if (current != null) {
            TrackInfo t = new TrackInfo(current.song, current.artist);
            current = t;
            notifyAllListeners(t);
            executor.execute(() -> {
                try {
                    TriviaRepository.fill(t);
                } catch (Throwable e) {
                    t.log("Chyba: " + e);
                } finally {
                    t.triviaDone = true;
                }
                if (current == t) notifyAllListeners(t);
            });
        }
    }
}
