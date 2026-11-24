/*
 * File:        SampleDataLoader.java
 * Module:      Sample data
 * Purpose:     Loads 30 sample reports, and on the emulator also sample news and an expired-token admin (seed profile).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.config;

import com.paayo.model.Admin;
import com.paayo.model.Category;
import com.paayo.model.NewsItem;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.newsbot.NewsScraper;
import com.paayo.newsbot.TextParaphraser;
import com.paayo.repository.AdminRepository;
import com.paayo.repository.NewsRepository;
import com.paayo.repository.ReportRepository;
import com.paayo.util.FirestoreTypes;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Fills an empty database for demos and QA. Runs only with the "seed" profile. */
@Component
@Profile("seed")
public class SampleDataLoader {
    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private static final Map<Category, List<String>> TITLES = Map.of(
            Category.SANITATION, List.of("Clogged Storm Drain", "Sidewalk Garbage Pile", "Overflowing Public Bin",
                    "Uncollected Garbage", "Flooded Street Corner", "Blocked Canal", "Litter Along the Creek",
                    "Missed Collection Day", "Foul Smell From Drainage", "Dumped Construction Waste"),
            Category.ROADWORKS, List.of("Deep Pothole", "Cracked Road Surface", "Broken Sidewalk", "Faded Crosswalk",
                    "Missing Manhole Cover", "Damaged Guardrail", "Sunken Road Section", "Unfinished Road Digging",
                    "Loose Gravel on Curve", "Damaged Bridge Joint"),
            Category.ELECTRICAL, List.of("Broken Streetlight", "Flickering Streetlight", "Dangling Wire",
                    "Leaning Electric Post", "Dark Alley at Night", "Exposed Wiring on Post", "Damaged Traffic Light",
                    "Streetlight On All Day", "Open Electrical Box", "Sparking Post Connection"));
    private static final List<String> BARANGAYS = List.of("Banilad", "Subangdaku", "Tipolo", "Guizo", "Alang-Alang",
            "Casuntingan", "Centro", "Paknaan", "Maguikay", "Cabancalan");
    private static final List<String> SAMPLE_PAGES =
            List.of("samples/sample-dpwh-region7.html", "samples/sample-mandaue-pio.html");

    private final ReportRepository reports;
    private final NewsRepository news;
    private final AdminRepository admins;
    private final NewsScraper scraper;
    private final TextParaphraser paraphraser;
    private final PasswordEncoder passwordEncoder;
    private final boolean emulator;

    /** Uses the repositories and the news parser; the emulator setting limits what may be loaded. */
    public SampleDataLoader(ReportRepository reports, NewsRepository news, AdminRepository admins, NewsScraper scraper,
                            TextParaphraser paraphraser, PasswordEncoder passwordEncoder,
                            @Value("${pa-ayo.firebase.emulator-host}") String emulatorHost) {
        this.reports = reports;
        this.news = news;
        this.admins = admins;
        this.scraper = scraper;
        this.paraphraser = paraphraser;
        this.passwordEncoder = passwordEncoder;
        this.emulator = !emulatorHost.isBlank();
    }

    /** Loads the samples unless reports already exist. Invented news and admins never reach production. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void load() throws IOException {
        if (!reports.findAll().isEmpty()) {
            log.info("Sample data skipped: the reports collection is not empty");
            return;
        }
        saveReports();
        if (emulator) {
            saveNews();
            saveExpiredAdmin();
        }
        log.info("Sample data loaded");
    }

    private void saveReports() {
        int number = 0;
        for (Category category : Category.values()) {
            List<String> titles = TITLES.get(category);
            for (int i = 0; i < titles.size(); i++, number++) {
                Report report = new Report();
                report.setCategory(category);
                report.setTitle(titles.get(i));
                report.setLocation("Sample St., " + BARANGAYS.get(i) + ", Mandaue City");
                report.setDescription("Sample report: " + titles.get(i).toLowerCase() + " near the barangay hall.");
                report.setImageUrl(i % 2 == 0 ? "/images/samples/" + category.name().toLowerCase() + ".png" : null);
                report.setStatus(ReportStatus.values()[i % ReportStatus.values().length]);
                report.setCreatedAt(FirestoreTypes.now().minusHours(7L * (number % 10) + number));
                report.setUpdatedAt(report.getCreatedAt());
                if (i == titles.size() - 1) {
                    useLongestValues(report);
                }
                reports.save(report);
            }
        }
    }

    // One report per category sits exactly on every length limit (100, 200 and 1000 characters).
    private static void useLongestValues(Report report) {
        report.setTitle(fill(report.getTitle() + " (longest title) ", 100));
        report.setLocation(fill(report.getLocation() + ", ", 200));
        report.setDescription(fill(report.getDescription() + " ", 1000));
    }

    private static String fill(String text, int length) {
        return text.repeat(length / text.length() + 1).substring(0, length - 1) + ".";
    }

    private void saveNews() throws IOException {
        for (String page : SAMPLE_PAGES) {
            try (InputStream in = new ClassPathResource(page).getInputStream()) {
                for (NewsItem item : scraper.parseArticles(Jsoup.parse(in, "UTF-8", "https://example.com/"))) {
                    if (item.getPublishedDate() == null) {
                        item.setPublishedDate(FirestoreTypes.today());
                    }
                    item.setSummary(paraphraser.paraphrase(item.getSummary()));
                    item.setFetchedAt(FirestoreTypes.now());
                    news.save(item);
                }
            }
        }
    }

    private void saveExpiredAdmin() {
        Admin admin = new Admin();
        admin.setUsername("expired.admin");
        admin.setEmail("expired.admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        admin.setResetTokenHash("0".repeat(64));
        admin.setResetTokenExpiresAt(FirestoreTypes.now().minusMinutes(31));
        admins.save(admin);
    }
}
