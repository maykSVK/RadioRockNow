package sk.radiorock.now;

import java.util.ArrayList;
import java.util.List;

/** Informácie o skladbe, ktorá práve hrá. */
public final class TrackInfo {
    public final String song;
    public final String artist;
    /** Krátke vety (na kartu v aute), strieda sa s menom interpreta. */
    public final List<String> shortFacts = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Dlhší text pre obrazovku v telefóne. */
    public String longText = "";
    /** Obal albumu (JPEG/PNG) alebo null. */
    public volatile byte[] artBytes;

    public TrackInfo(String song, String artist) {
        this.song = song;
        this.artist = artist;
    }
}

