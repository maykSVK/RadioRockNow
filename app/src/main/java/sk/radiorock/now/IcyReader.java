package sk.radiorock.now;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pripojí sa na stream Rádia ROCK, zahadzuje zvuk a číta len ICY metadáta (StreamTitle).
 * Používa nižšiu kvalitu streamu, aby sa minimalizovali dáta.
 */
final class IcyReader {
    interface Listener {
        void onStreamTitle(String rawTitle);
    }

    private static final String STREAM_URL = "https://stream.bauermedia.sk/rock-lo.mp3";
    private static final Pattern TITLE = Pattern.compile("StreamTitle='(.*?)'(;|$)", Pattern.DOTALL);

    private final Listener listener;
    private volatile boolean running;
    private volatile HttpURLConnection connection;
    private Thread thread;

    IcyReader(Listener listener) {
        this.listener = listener;
    }

    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "icy-reader");
        thread.setDaemon(true);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        HttpURLConnection c = connection;
        if (c != null) {
            new Thread(c::disconnect).start();
        }
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(STREAM_URL).openConnection();
                connection = c;
                c.setRequestProperty("Icy-MetaData", "1");
                c.setRequestProperty("User-Agent", "RadioRockNow/1.0");
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                int metaInt = c.getHeaderFieldInt("icy-metaint", 0);
                if (metaInt <= 0) throw new IOException("No icy-metaint");
                InputStream in = c.getInputStream();
                byte[] audio = new byte[metaInt];
                while (running) {
                    readFully(in, audio, metaInt);
                    int len = in.read();
                    if (len < 0) throw new EOFException();
                    len *= 16;
                    if (len > 0) {
                        byte[] meta = new byte[len];
                        readFully(in, meta, len);
                        String s = new String(meta, StandardCharsets.UTF_8);
                        Matcher m = TITLE.matcher(s);
                        if (m.find()) listener.onStreamTitle(m.group(1).trim());
                    }
                }
            } catch (Exception e) {
                // pripojenie sa prerušilo – skúsime znova
            } finally {
                HttpURLConnection c = connection;
                if (c != null) c.disconnect();
            }
            if (!running) break;
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    private static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
    }
}
