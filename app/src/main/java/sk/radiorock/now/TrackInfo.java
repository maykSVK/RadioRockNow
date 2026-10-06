package sk.radiorock.now;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Informácie o skladbe, ktorá práve hrá. */
public final class TrackInfo {
    public final String song;
    public final String artist;
    /** Krátke fakty vo formáte "Kľúč: hodnota". */
    public final List<String> shortFacts = new CopyOnWriteArrayList<>();
    /** Úvod o interpretovi z Wikipédie (môže byť null). */
    public volatile String wikiText;
    /** Dlhší text pre obrazovku v telefóne. */
    public volatile String longText = "";
    /** Obal albumu (JPEG/PNG) alebo null. */
    public volatile byte[] artBytes;
    /** True, keď vyhľadávanie zaujímavostí skončilo (aj neúspešne). */
    public volatile boolean triviaDone;
    /** Diagnostika: čo sa podarilo / nepodarilo pri načítaní. */
    public final List<String> debug = new CopyOnWriteArrayList<>();

    public TrackInfo(String song, String artist) {
        this.song = song;
        this.artist = artist;
    }

    /** Krátky text s faktami do jedného riadku (pre pole „album" v aute). */
    public String factsLine() {
        StringBuilder sb = new StringBuilder();
        for (String f : shortFacts) {
            int i = f.indexOf(": ");
            String v = i > 0 ? f.substring(i + 2) : f;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(v);
        }
        return sb.toString();
    }

    /** Dvojice (nadpis, podnadpis) pre zoznam zaujímavostí v aute. */
    public List<String[]> detailItems() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{song, artist});
        for (String f : shortFacts) {
            int i = f.indexOf(": ");
            if (i > 0) out.add(new String[]{f.substring(i + 2), f.substring(0, i)});
        }
        String w = wikiText;
        if (w != null && !w.isEmpty()) {
            String[] parts = w.split("(?<=[.!?])\\s+");
            int n = 0;
            for (String p : parts) {
                if (p.trim().length() < 15) continue;
                out.add(new String[]{p.trim(), n == 0 ? "O interpretovi" : ""});
                if (++n >= 3) break;
            }
        }
        if (!triviaDone) out.add(new String[]{"Načítavam zaujímavosti…", ""});
        else if (out.size() == 1) out.add(new String[]{"Zaujímavosti sa nenašli", ""});
        return out;
    }

    public void log(String s) {
        debug.add(s);
    }
}
