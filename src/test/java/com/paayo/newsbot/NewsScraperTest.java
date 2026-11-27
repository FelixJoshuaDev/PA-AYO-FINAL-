/*
 * File:        NewsScraperTest.java
 * Module:      Testing
 * Purpose:     Checks page parsing on offline fixtures and fetching on a local test server (TC-07, TC-09, TC-10).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.newsbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.paayo.model.NewsItem;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for NewsScraper. No test contacts a real website (NFR-15). */
class NewsScraperTest {
    private static final List<String> KEYWORDS = List.of("road", "bridge", "drainage", "flood", "garbage", "streetlight");
    private static final int TIMEOUT_MS = 300;

    private HttpServer server;
    private String base;
    private final AtomicReference<String> userAgent = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void serve(String path, int status, String body) {
        server.createContext(path, exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private static NewsScraper scraper(String... urls) {
        return new NewsScraper(List.of(urls), "TestBot/1.0", TIMEOUT_MS, KEYWORDS);
    }

    private static Document fixture(String path) throws IOException {
        try (InputStream in = NewsScraperTest.class.getResourceAsStream(path)) {
            return Jsoup.parse(in, "UTF-8", "https://news.example/");
        }
    }

    private static List<NewsItem> parse(String html) {
        return scraper().parseArticles(Jsoup.parse(html, "https://news.example/list"));
    }

    private static String article(String content) {
        return "<article>" + content + "</article>";
    }

    /** TC-07: title, text, date, thumbnail and link are extracted. */
    @Test
    void extractsEveryField() throws IOException {
        List<NewsItem> items = scraper().parseArticles(fixture("/samples/sample-dpwh-region7.html"));

        assertThat(items).hasSize(3);
        NewsItem first = items.get(0);
        assertThat(first.getTitle()).isEqualTo("[SAMPLE] Road Repair Advisory for M.C. Briones Street");
        assertThat(first.getSummary()).startsWith("Nighttime asphalt reblocking");
        assertThat(first.getSourceName()).isEqualTo("SAMPLE - DPWH Region VII");
        assertThat(first.getSourceUrl()).isEqualTo("https://news.example/news/road-repair");
        assertThat(first.getImageUrl()).isEqualTo("https://news.example/images/sample-road.jpg");
        assertThat(first.getPublishedDate()).isEqualTo(LocalDate.of(2026, 11, 19));
    }

    /** TC-09: a missing date or thumbnail is not an error. */
    @Test
    void leavesAMissingDateOrThumbnailEmpty() throws IOException {
        List<NewsItem> items = scraper().parseArticles(fixture("/samples/sample-dpwh-region7.html"));

        assertThat(items.get(1).getImageUrl()).isNull();
        assertThat(items.get(2).getPublishedDate()).isNull();
    }

    @Test
    void readsShortMonthNames() throws IOException {
        assertThat(scraper().parseArticles(fixture("/samples/sample-mandaue-pio.html")))
                .extracting(NewsItem::getPublishedDate).first().isEqualTo(LocalDate.of(2026, 11, 18));
    }

    /** The markup of the live cebu.gov.ph listings: loop items with category links, srcset and decorative letters. */
    @Test
    void readsTheProvinceOfCebuLayout() throws IOException {
        List<NewsItem> items = scraper().parseArticles(fixture("/fixtures/cebu-province-listing.html"));

        assertThat(items).hasSize(1);
        NewsItem item = items.get(0);
        assertThat(item.getTitle()).isEqualTo("ROAD Project Opens in Sample Town");
        assertThat(item.getSourceUrl()).isEqualTo("https://news.example/101/road-project-opens/");
        assertThat(item.getImageUrl()).isEqualTo("https://news.example/uploads/photo-300x169.jpg");
        assertThat(item.getPublishedDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(item.getSummary()).isEmpty();
    }

    @Test
    void keepsOnlyInfrastructureNews() {
        assertThat(parse(article("<h3><a href='/a'>Roadside snacks festival</a></h3>")
                + article("<h3><a href='/b'>Bridges reopen</a></h3>"))).extracting(NewsItem::getTitle)
                .containsExactly("Bridges reopen");
    }

    /** Casino spam planted on cebu.gov.ph uses road words but is always "uncategorized". */
    @Test
    void skipsUncategorizedSpam() {
        assertThat(parse("<div class='hentry category-uncategorized'><h3><a href='/36736/offers-on-the-road/'>"
                + "By doing this you can meet up with the brand new offers on the road</a></h3></div>"
                + "<div class='hentry category-news'><h3><a href='/14113/diversion-road/'>Diversion Road opens</a></h3></div>"))
                .extracting(NewsItem::getTitle).containsExactly("Diversion Road opens");
    }

    @Test
    void ignoresOtherSitesAndBrokenLinks() {
        assertThat(parse(article("<h3><a href='https://ads.example/x'>Road ad</a></h3>")
                + article("<h3><a href='https://bad host/x'>Road link</a></h3>")
                + article("<p>Road without a title</p>"))).isEmpty();
        assertThat(scraper().parseArticles(Jsoup.parse(article("<h3><a href='/a'>Road</a></h3>")))).isEmpty();
    }

    @Test
    void usesTheHostNameWhenThePageHasNoSiteName() {
        assertThat(parse(article("<h3><a href='/a#top'>Road news</a></h3><img src='data:image/gif;base64,AAAA'>")))
                .singleElement().satisfies(item -> {
                    assertThat(item.getSourceName()).isEqualTo("news.example");
                    assertThat(item.getSourceUrl()).isEqualTo("https://news.example/a");
                    assertThat(item.getImageUrl()).isNull();
                });
    }

    @Test
    void ignoresImpossibleDates() {
        assertThat(parse(article("<h3><a href='/a'>Road news</a></h3><p>Febtober 31, 2026</p>")))
                .singleElement().extracting(NewsItem::getPublishedDate).isNull();
    }

    @Test
    void takesAtMostFiveArticlesPerSource() {
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            html.append(article("<h3><a href='/" + i + "'>Road news " + i + "</a></h3>"));
        }

        assertThat(parse(html.toString())).hasSize(5);
    }

    @Test
    void identifiesItselfToTheServer() throws IOException {
        serve("/page", 200, "<p>hello</p>");

        assertThat(scraper().fetchPage(base + "/page").text()).isEqualTo("hello");
        assertThat(userAgent.get()).isEqualTo("TestBot/1.0");
    }

    /** TC-10: unreachable, slow, failing and redesigned sources do not stop the others. */
    @Test
    void skipsFailingSources() {
        serve("/error", 500, "broken");
        serve("/redesigned", 200, "<div>Nothing the bot knows</div>");
        serve("/good", 200, article("<h3><a href='/a'>Road news</a></h3>"));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(TIMEOUT_MS * 4L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        List<NewsItem> items = scraper("http://127.0.0.1:1/", "not a url", base + "/error", base + "/redesigned",
                base + "/good", base + "/slow").scrape();

        assertThat(items).extracting(NewsItem::getTitle).containsExactly("Road news");
    }

    @Test
    void readsArticleTextWithoutHeadings() {
        serve("/article", 200, "<nav><p>Menu</p></nav><div class='elementor-widget-theme-post-content'>"
                + "<p>\uD835\uDC11\uD835\uDC0E\uD835\uDC00\uD835\uDC03 NEWS</p><p>The road is open.</p><p>It is safe.</p></div>");

        assertThat(scraper().fetchArticleText(base + "/article")).isEqualTo("The road is open. It is safe.");
    }

    @Test
    void returnsNoTextWhenAnArticleCannotBeRead() {
        serve("/empty", 200, "<div>No paragraphs</div>");

        assertThat(scraper().fetchArticleText(base + "/empty")).isEmpty();
        assertThat(scraper().fetchArticleText(base + "/missing")).isEmpty();
    }
}
