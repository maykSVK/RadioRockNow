package sk.radiorock.now;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Generické zaujímavosti v slovenčine: MusicBrainz (rok, album, pôvod, žáner)
 * + slovenská Wikipédia cez Wikidata (úvod o interpretovi), ak existuje.
 */
final class TriviaRepository {
    private static final String UA = "RadioRockNow/1.0 (personal Android Auto app)";
    private static long lastMusicBrainz = 0;

    private TriviaRepository() {}

    static void fill(TrackInfo t) {
        StringBuilder facts = new StringBuilder();
        String wiki = null;
        try {
            String q = "recording:\"" + esc(t.song) + "\" AND artist:\"" + esc(t.artist) + "\"";
            JSONObject r = mb("recording/?fmt=json&limit=15&query=" + enc(q));
            JSONArray recs = r.optJSONArray("recordings");
            if (recs == null || recs.length() == 0) return;
            if (recs.getJSONObject(0).optInt("score", 0) < 70) return;

            // najskorší rok spomedzi silných zhôd = pôvodné vydanie
            JSONObject rec = recs.getJSONObject(0);
            String year = null;
            for (int i = 0; i < recs.length(); i++) {
                JSONObject c = recs.getJSONObject(i);
                if (c.optInt("score", 0) < 90) continue;
                String fr = c.optString("first-release-date", "");
                if (fr.length() >= 4 && (year == null || fr.substring(0, 4).compareTo(year) < 0)) {
                    year = fr.substring(0, 4);
                    rec = c;
                }
            }

            // štúdiový album (nie live / compilation) s najskorším dátumom vydania
            String album = null;
            String rgId = null;
            String albumDate = null;
            for (int i = 0; i < recs.length(); i++) {
                JSONObject c = recs.getJSONObject(i);
                if (c.optInt("score", 0) < 90) continue;
                JSONArray rels = c.optJSONArray("releases");
                if (rels == null) continue;
                for (int j = 0; j < rels.length(); j++) {
                    JSONObject rel = rels.getJSONObject(j);
                    JSONObject rg = rel.optJSONObject("release-group");
                    if (rg == null || !"Album".equals(rg.optString("primary-type"))
                            || rg.optJSONArray("secondary-types") != null) continue;
                    String d = rel.optString("date", "9999");
                    if (album == null || d.compareTo(albumDate) < 0) {
                        album = rel.optString("title", null);
                        rgId = rg.optString("id", null);
                        albumDate = d;
                    }
                }
            }

            if (year != null) t.shortFacts.add("Rok vydania: " + year);
            if (album != null) t.shortFacts.add("Album: " + album);
            if (rgId != null) t.artBytes = download("https://coverartarchive.org/release-group/" + rgId + "/front-250");

            StringBuilder sb = new StringBuilder();
            sb.append("Skladba „").append(t.song).append("“");
            if (year != null) sb.append(" vyšla v roku ").append(year);
            else sb.append(" je od interpreta ").append(t.artist);
            if (album != null) sb.append(" na albume „").append(album).append("“");
            sb.append(".");
            facts.append(sb);

            JSONArray credit = rec.optJSONArray("artist-credit");
            String artistId = null;
            if (credit != null && credit.length() > 0) {
                JSONObject a = credit.getJSONObject(0).optJSONObject("artist");
                if (a != null) artistId = a.optString("id", null);
            }
            if (artistId != null) {
                JSONObject a = mb("artist/" + artistId + "?fmt=json&inc=url-rels+genres");
                String type = a.optString("type", "");
                String gender = a.optString("gender", "");
                boolean group = "Group".equals(type) || "Orchestra".equals(type);
                JSONObject span = a.optJSONObject("life-span");
                String begin = span != null ? span.optString("begin", "") : "";
                String by = begin.length() >= 4 ? begin.substring(0, 4) : null;
                String country = null;
                String cc = a.optString("country", "");
                if (cc.length() == 2 && !cc.equals("XW") && !cc.equals("XE")) {
                    country = new Locale("", cc).getDisplayCountry(new Locale("sk"));
                }
                if (country == null || country.isEmpty()) {
                    JSONObject area = a.optJSONObject("area");
                    if (area != null) country = area.optString("name", null);
                }
                if (country != null && !country.isEmpty()) {
                    t.shortFacts.add("Pôvod: " + country);
                }
                if (by != null) {
                    t.shortFacts.add((group ? "Vznik: " : "Narodenie: ") + by);
                }
                JSONArray genres = a.optJSONArray("genres");
                if (genres != null && genres.length() > 0) {
                    String best = null;
                    int bestCount = -1;
                    for (int i = 0; i < genres.length(); i++) {
                        JSONObject g = genres.getJSONObject(i);
                        if (g.optInt("count", 0) > bestCount) {
                            bestCount = g.optInt("count", 0);
                            best = g.optString("name");
                        }
                    }
                    if (best != null) t.shortFacts.add("Žáner: " + best);
                }

                StringBuilder who = new StringBuilder();
                who.append(" ").append(t.artist).append(" – ");
                if (group) who.append("skupina");
                else if ("Female".equals(gender)) who.append("interpretka");
                else who.append("interpret");
                if (country != null && !country.isEmpty()) who.append(", pôvod: ").append(country);
                if (by != null) who.append(group ? ", vznik: " : ", nar.: ").append(by);
                who.append(".");
                facts.append(who);

                wiki = skWikipedia(a.optJSONArray("relations"));
                if (wiki != null && !wiki.isEmpty()) {
                    String first = firstSentences(wiki, 170);
                    int dot = first.indexOf(". ");
                    if (dot > 40) first = first.substring(0, dot + 1);
                    t.shortFacts.add(first);
                }
            }
        } catch (Exception ignored) {
            // zaujímavosti sú voliteľné
        } finally {
            StringBuilder out = new StringBuilder(facts);
            if (wiki != null && !wiki.isEmpty()) out.append("\n\n").append(wiki);
            t.longText = out.toString();
        }
    }

    private static String skWikipedia(JSONArray relations) {
        try {
            if (relations == null) return null;
            String qid = null;
            for (int i = 0; i < relations.length(); i++) {
                JSONObject rel = relations.getJSONObject(i);
                if ("wikidata".equals(rel.optString("type"))) {
                    JSONObject url = rel.optJSONObject("url");
                    if (url != null) {
                        String res = url.optString("resource", "");
                        qid = res.substring(res.lastIndexOf('/') + 1);
                    }
                }
            }
            if (qid == null || qid.isEmpty()) return null;
            JSONObject wd = getJson("https://www.wikidata.org/w/api.php?action=wbgetentities&props=sitelinks&sitefilter=skwiki%7Cenwiki&format=json&ids=" + qid);
            JSONObject sl = wd.getJSONObject("entities").getJSONObject(qid).optJSONObject("sitelinks");
            if (sl == null) return null;
            String lang = "sk";
            JSONObject sk = sl.optJSONObject("skwiki");
            if (sk == null) {
                sk = sl.optJSONObject("enwiki");
                lang = "en";
            }
            if (sk == null) return null;
            String title = sk.getString("title");
            JSONObject sum = getJson("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + enc(title).replace("+", "%20"));
            String text = sum.optString("extract", "");
            return firstSentences(text, 320);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstSentences(String text, int max) {
        if (text.length() <= max) return text;
        int end = text.lastIndexOf(". ", max);
        if (end > 60) return text.substring(0, end + 1);
        return text.substring(0, max).trim() + "…";
    }

    private static JSONObject mb(String path) throws Exception {
        synchronized (TriviaRepository.class) {
            long wait = 1100 - (System.currentTimeMillis() - lastMusicBrainz);
            if (wait > 0) Thread.sleep(wait);
            try {
                return getJson("https://musicbrainz.org/ws/2/" + path);
            } finally {
                lastMusicBrainz = System.currentTimeMillis();
            }
        }
    }

    private static byte[] download(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", UA);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                return bo.toByteArray();
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
