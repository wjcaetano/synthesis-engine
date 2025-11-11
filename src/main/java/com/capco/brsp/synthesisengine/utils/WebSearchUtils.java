package com.capco.brsp.synthesisengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class WebSearchUtils {

    private static final WebSearchUtils INSTANCE = new WebSearchUtils();

    private WebSearchUtils() {
    }

    public static WebSearchUtils getInstance() {
        return INSTANCE;
    }
    // ===================== Public API =====================

    /**
     * Fetch the URL and return normalized JSON (pretty) with results.
     */
    public static Object parseFromUrl(String url, int maxResults) throws Exception {
        String html = fetchHtml(url);
        return parseToJson(url, html, maxResults);
    }

    /**
     * Parse given HTML (already fetched) into normalized JSON using the source URL to detect engine.
     */
    public static Object parseToJson(String sourceUrl, String html, int maxResults) throws Exception {
        Engine engine = detectEngine(sourceUrl);

        List<Map<String, Object>> items = new ConcurrentLinkedList<>();
        int count;

        switch (engine) {
            case ARXIV:
                count = parseArxiv(html, sourceUrl, items, maxResults);
                break;

            case DUCKDUCKGO:
                count = parseDuckDuckGo(html, sourceUrl, items, maxResults);
                break;

            case BING:
                count = parseBing(html, sourceUrl, items, maxResults);
                break;

            default:
                count = parseBing(html, sourceUrl, items, maxResults);
                if (count == 0) count = parseDuckDuckGo(html, sourceUrl, items, maxResults);
        }

        var root = new ConcurrentLinkedHashMap<>();
        root.put("engine", engine.name().toLowerCase(Locale.ROOT));
        root.put("source_url", sourceUrl);
        root.put("result_count", count);
        root.put("results", items);

        return root;
    }

    // ================== Implementation ====================

    private enum Engine {ARXIV, BING, DUCKDUCKGO, UNKNOWN}

    private static Engine detectEngine(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return Engine.UNKNOWN;
            String h = host.toLowerCase(Locale.ROOT);
            if (h.contains("arxiv")) return Engine.ARXIV;
            if (h.contains("duckduckgo")) return Engine.DUCKDUCKGO;
            if (h.contains("bing")) return Engine.BING;
        } catch (Exception ignore) {
        }
        return Engine.UNKNOWN;
    }

    // FOLLOW REDIRECTS to handle DDG /html 302, etc.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static String fetchHtml(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) return resp.body();
        throw new RuntimeException("HTTP " + resp.statusCode() + " fetching " + url);
    }

    // ---------------- DuckDuckGo ----------------

    /**
     * If the page is the JS shell, refetch /html/?q=... (or /lite) to get server-rendered results.
     */
    private static String ensureDdgServerHtml(String sourceUrl, String html) throws Exception {
        Document doc = Jsoup.parse(html, sourceUrl);

        // Already server-rendered?
        if (!doc.select("div.result a.result__a, div.result--web a.result__a, div.web-result a.result__a").isEmpty()) {
            return html;
        }

        // 1) Use <noscript> fallback if present
        Element noscriptA = doc.selectFirst("noscript a[href*=/html/?q=]");
        if (noscriptA != null) {
            String fallbackUrl = noscriptA.absUrl("href");
            return fetchHtml(fallbackUrl);
        }

        // 2) Build /html URL from query param
        String q = getQueryParam(sourceUrl, "q");
        if (q != null && !q.isBlank()) {
            StringBuilder fb = new StringBuilder("https://duckduckgo.com/html/?q=")
                    .append(URLEncoder.encode(q, StandardCharsets.UTF_8));
            String kl = getQueryParam(sourceUrl, "kl"); // region
            String df = getQueryParam(sourceUrl, "df"); // time filter
            if (kl != null && !kl.isBlank()) fb.append("&kl=").append(URLEncoder.encode(kl, StandardCharsets.UTF_8));
            if (df != null && !df.isBlank()) fb.append("&df=").append(URLEncoder.encode(df, StandardCharsets.UTF_8));
            return fetchHtml(fb.toString());
        }

        // 3) Last resort: lite
        String liteUrl = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode("test", StandardCharsets.UTF_8);
        return fetchHtml(liteUrl);
    }

    private static int parseDuckDuckGo(String html, String base, List<Map<String, Object>> items, int maxResults) throws Exception {
        // Convert JS shell to server HTML if needed
        html = ensureDdgServerHtml(base, html);

        Document doc = Jsoup.parse(html, base);

        // Main /html layout
        Elements blocks = doc.select("div.result, div.result--web, div.web-result");
        int pos = 0;
        for (Element block : blocks) {
            Element a = block.selectFirst("a.result__a[href]");
            if (a == null) continue;

            String title = a.text();
            String absHref = a.absUrl("href");
            String link = decodeDuckHref(absHref);

            String snippet = "";
            Element sn = block.selectFirst("div.result__snippet, a.result__snippet, div.result__body .result__snippet");
            if (sn != null) snippet = sn.text();

            if (!title.isBlank() && !link.isBlank()) {
                items.add(resultNode(++pos, title, link, snippet));
                if (pos >= maxResults) break;
            }
        }

        // Fallback: "lite" layout (very simple)
        if (pos == 0) {
            Elements liteLinks = doc.select("table a[href]:not([href^=/y.js])");
            for (Element a : liteLinks) {
                String title = a.text();
                String link = a.absUrl("href");
                if (!title.isBlank() && !link.isBlank()) {
                    items.add(resultNode(++pos, title, link, ""));
                    if (pos >= maxResults) break;
                }
            }
        }

        return pos;
    }

    private static String decodeDuckHref(String absUrl) {
        try {
            URI uri = URI.create(absUrl);
            String path = uri.getPath();
            if ("/l/".equals(path) || "/l".equals(path)) {
                Map<String, String> q = splitQuery(uri.getRawQuery());
                String uddg = q.get("uddg");
                if (uddg != null) return urlDecode(uddg);
            }
            return absUrl;
        } catch (Exception e) {
            return absUrl;
        }
    }

    // ---------------- Bing ----------------

    private static int parseBing(String html, String base, List<Map<String, Object>> items, int maxResults) throws Exception {
        Document doc = Jsoup.parse(html, base);

        // Detect Cloudflare/Bot challenge page (no real SERP present)
        boolean challenged =
                doc.selectFirst("div.captcha, #cf-please-wait") != null ||
                        doc.selectFirst("script[src*=\"cloudflare.com/turnstile\"], script[src*=\"/challenge/verify\"]") != null ||
                        doc.title().toLowerCase(Locale.ROOT).contains("one last step");

        if (challenged) {
            return parseBingRssFallback(base, items, maxResults);
        }

        Elements blocks = doc.select("li.b_algo"); // normal organic results
        int pos = 0;
        for (Element b : blocks) {
            Element a = b.selectFirst("h2 > a[href]");
            if (a == null) continue;

            String title = a.text();
            String href = a.attr("href");
            String link = decodeBingHref(href, a.absUrl("href"));

            String snippet = "";
            Element p = b.selectFirst("div.b_caption p, div.b_caption>p");
            if (p != null) snippet = p.text();

            if (!title.isBlank() && !link.isBlank()) {
                items.add(resultNode(++pos, title, link, snippet));
                if (pos >= maxResults) break;
            }
        }

        // If Bing returned a weird shell and we found nothing, try RSS as a safety net
        if (pos == 0) {
            return parseBingRssFallback(base, items, maxResults);
        }
        return pos;
    }

    private static int parseBingRssFallback(String sourceUrl, List<Map<String, Object>> items, int maxResults) throws Exception {
        // Build RSS URL from sourceUrl's q + carry over cc/setlang if present
        String base = "https://www.bing.com/search?format=rss";
        String q = getQueryParam(sourceUrl, "q");
        String cc = getQueryParam(sourceUrl, "cc");
        String setlang = getQueryParam(sourceUrl, "setlang");
        String count = getQueryParam(sourceUrl, "count"); // optional

        StringBuilder rss = new StringBuilder(base);
        if (q != null) rss.append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
        if (cc != null) rss.append("&cc=").append(URLEncoder.encode(cc, StandardCharsets.UTF_8));
        if (setlang != null) rss.append("&setlang=").append(URLEncoder.encode(setlang, StandardCharsets.UTF_8));
        if (count != null) rss.append("&count=").append(URLEncoder.encode(count, StandardCharsets.UTF_8));

        String xml = fetchHtml(rss.toString());
        Document doc = Jsoup.parse(xml, rss.toString(), org.jsoup.parser.Parser.xmlParser());

        Elements itemsEls = doc.select("rss > channel > item");
        int pos = 0;
        for (Element it : itemsEls) {
            String title = it.selectFirst("title") != null ? it.selectFirst("title").text() : "";
            String link = it.selectFirst("link") != null ? it.selectFirst("link").text() : "";
            String snippet = it.selectFirst("description") != null ? it.selectFirst("description").text() : "";
            if (!title.isBlank() && !link.isBlank()) {
                items.add(resultNode(++pos, title, link, snippet));
                if (pos >= maxResults) break;
            }
        }
        return pos;
    }

    private static String decodeBingHref(String rawHref, String absHref) {
        try {
            URI uri = URI.create(absHref);
            String path = uri.getPath();
            Map<String, String> q = splitQuery(uri.getRawQuery());

            if ("/ck/a".equals(path) || "/ck/a/".equals(path)) {
                String u = q.get("u");
                if (u != null) {
                    String decoded = urlDecode(u);
                    try {
                        String maybe = new String(Base64.getDecoder().decode(decoded), StandardCharsets.UTF_8);
                        if (maybe.startsWith("http")) return maybe;
                    } catch (IllegalArgumentException ignore) {
                    }
                    if (decoded.startsWith("http")) return decoded;
                }
            }

            if ("/url".equals(path)) {
                String direct = q.get("url");
                if (direct != null) return urlDecode(direct);
            }

            return !absHref.isBlank() ? absHref : rawHref;
        } catch (Exception e) {
            return (absHref != null && !absHref.isBlank()) ? absHref : rawHref;
        }
    }

    // ---------------- arXiv ----------------

    /**
     * Parse arXiv search results.
     * Supports current markup:
     *   <li class="arxiv-result">
     *     <p class="title is-5 mathjax"><a href="/abs/...">Title</a></p>
     *     <p class="abstract mathjax">Abstract: ...</p>
     *   </li>
     *
     * Falls back to older patterns if needed.
     */
    private static int parseArxiv(String html, String base, List<Map<String, Object>> items, int maxResults) {
        Document doc = Jsoup.parse(html, base);

        int pos = 0;

        // Primary (current) layout
        Elements results = doc.select("li.arxiv-result");
        for (Element r : results) {
            // Title: prefer the dedicated title element, which is usually not a link
            Element titleEl = r.selectFirst("p.title, h1.title, h2.title");
            String title = (titleEl != null) ? titleEl.text().trim() : "";

            // Link: usually in the list-title block; fall back to any /abs/ link in this result
            Element linkEl = r.selectFirst("p.list-title a[href*=/abs/], a[href*://arxiv.org/abs/], a[href^=/abs/]");
            String link = (linkEl != null) ? linkEl.absUrl("href").replace("/abs/", "/html/") : "";

            // Abstract/snippet (your existing logic with a small null-guard tweak)
            String snippet = "";
            Element abs = r.selectFirst("p.abstract, span.abstract, div.abstract");
            if (abs != null) {
                snippet = abs.text();
                if (snippet.regionMatches(true, 0, "abstract:", 0, 9)) {
                    snippet = snippet.substring(9).trim();
                }
            }

            if (!title.isBlank() && !link.isBlank()) {
                items.add(resultNode(++pos, title, link, snippet));
                if (pos >= maxResults) break;
            }
        }

        // If nothing matched, try a very conservative fallback
        if (pos == 0) {
            Elements anchors = doc.select("a[href^=/abs/], a[href*://arxiv.org/abs/]");
            for (Element a : anchors) {
                String title = a.text();
                String link = a.absUrl("href").replace("/abs/", "/html/");
                if (!title.isBlank() && !link.isBlank()) {
                    items.add(resultNode(++pos, title, link, ""));
                    if (pos >= maxResults) break;
                }
            }
        }

        return pos;
    }

    // ---------------- Utilities ----------------

    private static Map<String, Object> resultNode(int pos, String title, String link, String snippet) {
        var n = new ConcurrentLinkedHashMap<String, Object>();

        n.put("position", pos);
        n.put("title", title);
        n.put("link", link);
        n.put("snippet", (snippet == null) ? "" : snippet);

        return n;
    }

    private static String getQueryParam(String url, String name) {
        try {
            String raw = URI.create(url).getRawQuery();
            if (raw == null) return null;
            for (String pair : raw.split("&")) {
                int i = pair.indexOf('=');
                String k = (i > 0) ? pair.substring(0, i) : pair;
                String v = (i > 0) ? pair.substring(i + 1) : "";
                if (k.equalsIgnoreCase(name)) return URLDecoder.decode(v, StandardCharsets.UTF_8);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> splitQuery(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = (i > 0) ? pair.substring(0, i) : pair;
            String v = (i > 0) ? pair.substring(i + 1) : "";
            map.put(k, v);
        }
        return map;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
