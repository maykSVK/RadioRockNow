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
 * Generické zaujímavosti v slovenčine. Zdroje (každý sa skúša nezávisle):
 * MusicBrainz (rok, album, pôvod, žáner), iTunes Search (záloha: rok, album, žáner, obal),
 * Wikipédia sk/en (úvod o interpretovi), Cover Art Archive (obal).
 */
final class TriviaRepository {
    private static final String UA = "RadioRockNow/1.0 ( https://github.com/maykSVK/RadioRockNow )";
    private static long lastMusicBrainz = 0;

    private TriviaRepository() {}

    private static final class Data {
        String year, album, rgId, country, born, genre, artUrl, wikiQid;
        boolean group;
        String gender = "";
        JSONArray relations;
    }

    static void fill(TrackInfo t) {
        Data d = new Data();
        try {
            musicBrainz(t, d);
        } catch (Throwable e) {
            t.log("MusicBrainz: " + msg(e));
        }
        if (d.year == null || d.album == null || d.genre == null) {
            try {
                itunes(t, d);
            } catch (Throwable e) {
                t.log("iTunes: " + msg(e));
            }
        }
        String wiki = null;
        try {
            if (d.relations != null) wiki = wikiFromRelations(d.relations, t);
        } catch (Throwable e) {
            t.log("Wikidata: " + msg(e));
        }
        if (wiki == null) {
            try {
                wiki = wikiSearch(t.artist, t);
            } catch (Throwable e) {
                t.log("Wikipédia: " + msg(e));
            }
        }
        if (d.rgId != null) t.artBytes = download("https://coverartarchive.org/release-group/" + d.rgId + "/front-250");
        if (t.artBytes == null && d.artUrl != null) t.artBytes = download(d.artUrl);

        if (d.year != null) t.shortFacts.add("Rok vydania: " + d.year);
        if (d.album != null) t.shortFacts.add("Album: " + d.album);
        if (d.country != null && !d.country.isEmpty()) t.shortFacts.add("Pôvod: " + d.country);
        if (d.born != null) t.shortFacts.add((d.group ? "Vznik: " : "Narodenie: ") + d.born);
        if (d.genre != null) t.shortFacts.add("Žáner: " + d.genre);
        if (wiki != null && !wiki.isEmpty()) {
            t.wikiText = wiki;
        }

        StringBuilder sb = new StringBuilder();
        if (d.year != null || d.album != null) {
            sb.append("Skladba „").append(t.song).append("“");
            if (d.year != null) sb.append(" vyšla v roku ").append(d.year);
            else sb.append(" je od interpreta ").append(t.artist);
            if (d.album != null) sb.append(" na albume „").append(d.album).append("“");
            sb.append(".");
        }
        if (d.country != null || d.born != null || d.genre != null) {
            sb.append(" ").append(t.artist).append(" – ");
            if (d.group) sb.append("skupina");
            else if ("Female".equals(d.gender)) sb.append("interpretka");
            else sb.append("interpret");
            if (d.country != null && !d.country.isEmpty()) sb.append(", pôvod: ").append(d.country);
            if (d.born != null) sb.append(d.group ? ", vznik: " : ", nar.: ").append(d.born);
            if (d.genre != null) sb.append(", žáner: ").append(d.genre);
            sb.append(".");
        }
        if (wiki != null && !wiki.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(wiki);
        }
        t.longText = sb.toString().trim();
    }

    // ---------------------------------------------------------------- MusicBrainz

    private static void musicBrainz(TrackInfo t, Data d) throws Exception {
        String q = "recording:\"" + esc(t.song) + "\" AND artist:\"" + esc(t.artist) + "\"";
        JSONObject r = mb("recording/?fmt=json&limit=15&query=" + enc(q));
        JSONArray recs = r.optJSONArray("recordings");
        if (recs == null || recs.length() == 0 || recs.getJSONObject(0).optInt("score", 0) < 70) {
            t.log("MusicBrainz: skladba nenájdená");
            return;
        }
        for (int i = 0; i < recs.length(); i++) {
            JSONObject c = recs.getJSONObject(i);
            if (c.optInt("score", 0) < 90) continue;
            String fr = c.optString("first-release-date", "");
            if (fr.length() >= 4 && (d.year == null || fr.substring(0, 4).compareTo(d.year) < 0)) {
                d.year = fr.substring(0, 4);
            }
        }
        String albumDate = null;
        String artistId = null;
        for (int i = 0; i < recs.length(); i++) {
            JSONObject c = recs.getJSONObject(i);
            if (c.optInt("score", 0) < 90) continue;
            if (artistId == null) {
                JSONArray credit = c.optJSONArray("artist-credit");
                if (credit != null && credit.length() > 0) {
                    JSONObject a = credit.getJSONObject(0).optJSONObject("artist");
                    if (a != null) artistId = a.optString("id", null);
                }
            }
            JSONArray rels = c.optJSONArray("releases");
            if (rels == null) continue;
            for (int j = 0; j < rels.length(); j++) {
                JSONObject rel = rels.getJSONObject(j);
                JSONObject rg = rel.optJSONObject("release-group");
                if (rg == null || !"Album".equals(rg.optString("primary-type"))
                        || rg.optJSONArray("secondary-types") != null) continue;
                String dt = rel.optString("date", "9999");
                if (d.album == null || dt.compareTo(albumDate) < 0) {
                    d.album = rel.optString("title", null);
                    d.rgId = rg.optString("id", null);
                    albumDate = dt;
                }
            }
        }
        if (artistId == null) return;
        JSONObject a = mb("artist/" + artistId + "?fmt=json&inc=url-rels+genres");
        String type = a.optString("type", "");
        d.gender = a.optString("gender", "");
        d.group = "Group".equals(type) || "Orchestra".equals(type);
        JSONObject span = a.optJSONObject("life-span");
        String begin = span != null ? span.optString("begin", "") : "";
        if (begin.length() >= 4) d.born = begin.substring(0, 4);
        String cc = a.optString("country", "");
        if (cc.length() == 2 && !cc.equals("XW") && !cc.equals("XE")) {
            d.country = new Locale("", cc).getDisplayCountry(new Locale("sk"));
        }
        if (d.country == null || d.country.isEmpty()) {
            JSONObject area = a.optJSONObject("area");
            if (area != null) d.country = area.optString("name", null);
        }
        JSONArray genres = a.optJSONArray("genres");
        if (genres != null) {
            int best = -1;
            for (int i = 0; i < genres.length(); i++) {
                JSONObject g = genres.getJSONObject(i);
                if (g.optInt("count", 0) > best) {
                    best = g.optInt("count", 0);
                    d.genre = g.optString("name");
                }
            }
        }
        d.relations = a.optJSONArray("relations");
        t.log("MusicBrainz: OK");
    }

    // ---------------------------------------------------------------- iTunes (záloha)

    private static void itunes(TrackInfo t, Data d) throws Exception {
        JSONObject r = getJson("https://itunes.apple.com/search?media=music&entity=song&limit=15&term="
                + enc(t.artist + " " + t.song));
        JSONArray res = r.optJSONArray("results");
        if (res == null) return;
        String na = norm(t.artist);
        String ns = norm(t.song);
        String bestDate = null;
        JSONObject best = null;
        for (int i = 0; i < res.length(); i++) {
            JSONObject o = res.getJSONObject(i);
            String oa = norm(o.optString("artistName"));
            String os = norm(o.optString("trackName"));
            if (!(oa.contains(na) || na.contains(oa)) || !os.startsWith(ns)) continue;
            String rd = o.optString("releaseDate", "9999");
            if (best == null || rd.compareTo(bestDate) < 0) {
                best = o;
                bestDate = rd;
            }
        }
        if (best == null) {
            t.log("iTunes: skladba nenájdená");
            return;
        }
        if (d.year == null && bestDate.length() >= 4 && !bestDate.startsWith("9999")) d.year = bestDate.substring(0, 4);
        if (d.album == null) {
            d.album = best.optString("collectionName", null);
            String art = best.optString("artworkUrl100", null);
            if (art != null) d.artUrl = art.replace("100x100", "600x600");
        }
        if (d.genre == null) d.genre = best.optString("primaryGenreName", null);
        t.log("iTunes: OK");
    }

    // ---------------------------------------------------------------- Wikipédia

    private static String wikiFromRelations(JSONArray relations, TrackInfo t) throws Exception {
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
        String text = summary(lang, sk.getString("title"));
        if (text != null) t.log("Wikipédia (" + lang + "): OK");
        return text;
    }

    /** Záloha: vyhľadá článok o interpretovi podľa mena. */
    private static String wikiSearch(String artist, TrackInfo t) throws Exception {
        String[][] tries = {
                {"sk", artist + " hudobník skupina spevák"},
                {"en", artist + " singer band musician"}
        };
        for (String[] tr : tries) {
            JSONObject s = getJson("https://" + tr[0] + ".wikipedia.org/w/api.php?action=query&list=search&srlimit=1&format=json&srsearch=" + enc(tr[1]));
            JSONArray arr = s.getJSONObject("query").optJSONArray("search");
            if (arr == null || arr.length() == 0) continue;
            String title = arr.getJSONObject(0).getString("title");
            String nt = norm(title), na = norm(artist);
            if (!(nt.contains(na) || na.contains(nt))) continue;
            String text = summary(tr[0], title);
            if (text != null) {
                t.log("Wikipédia (" + tr[0] + ", hľadanie): OK");
                return text;
            }
        }
        t.log("Wikipédia: nenájdená");
        return null;
    }

    private static String summary(String lang, String title) throws Exception {
        JSONObject sum = getJson("https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/" + enc(title).replace("+", "%20"));
        if ("disambiguation".equals(sum.optString("type"))) return null;
        String text = sum.optString("extract", "");
        if (text.isEmpty()) return null;
        return firstSentences(text, 320);
    }

    private static String firstSentences(String text, int max) {
        if (text.length() <= max) return text;
        int end = text.lastIndexOf(". ", max);
        if (end > 60) return text.substring(0, end + 1);
        return text.substring(0, max).trim() + "…";
    }

    // ---------------------------------------------------------------- HTTP

    private static JSONObject mb(String path) throws Exception {
        synchronized (TriviaRepository.class) {
            for (int attempt = 0; ; attempt++) {
                long wait = 1100 - (System.currentTimeMillis() - lastMusicBrainz);
                if (wait > 0) Thread.sleep(wait);
                try {
                    return getJson("https://musicbrainz.org/ws/2/" + path);
                } catch (java.io.IOException e) {
                    if (attempt >= 1) throw e;
                    Thread.sleep(2500);
                } finally {
                    lastMusicBrainz = System.currentTimeMillis();
                }
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

    private static String msg(Throwable e) {
        String m = e.getMessage();
        if (m != null && m.length() > 80) m = m.substring(0, 80);
        return e.getClass().getSimpleName() + (m != null ? " – " + m : "");
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
