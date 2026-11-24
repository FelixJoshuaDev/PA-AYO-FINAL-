/*
 * File:        RepositoryIT.java
 * Module:      Testing
 * Purpose:     Checks every repository operation against the Firestore emulator (WBS 1.4.3).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.firestore.Firestore;
import com.paayo.config.FirebaseConfig;
import com.paayo.model.Admin;
import com.paayo.model.Category;
import com.paayo.model.NewsItem;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.testutil.Emulator;
import com.paayo.util.FirestoreTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integration tests for the repositories; needs the emulator on 127.0.0.1:8085. */
class RepositoryIT {
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 20, 8, 30);

    private final Firestore firestore = new FirebaseConfig("", Emulator.PROJECT_ID, Emulator.HOST).firestore();
    private final ReportRepository reports = new ReportRepository(firestore);
    private final NewsRepository news = new NewsRepository(firestore);
    private final AdminRepository admins = new AdminRepository(firestore);

    @BeforeEach
    void clear() throws Exception {
        Emulator.clear();
    }

    private static Report report(Category category, LocalDateTime createdAt) {
        Report report = new Report();
        report.setTitle("Clogged Storm Drain");
        report.setCategory(category);
        report.setLocation("A.S. Fortuna St., Banilad");
        report.setDescription("Debris blocking the main storm drain.");
        report.setStatus(ReportStatus.REPORTED);
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        return report;
    }

    @Test
    void savesAReportWithAnExplicitNullPhoto() throws Exception {
        Report saved = reports.save(report(Category.SANITATION, TIME));

        Map<String, Object> stored = firestore.collection("reports").document(saved.getReportId()).get().get().getData();
        assertThat(stored).containsOnlyKeys("title", "category", "location", "description", "imageUrl", "status",
                "createdAt", "updatedAt");
        assertThat(stored).containsEntry("imageUrl", null).containsEntry("category", "SANITATION");
        assertThat(reports.findById(saved.getReportId()).getCreatedAt()).isEqualTo(TIME);
    }

    @Test
    void listsNewestFirstAndFiltersByCategory() {
        Report older = reports.save(report(Category.ROADWORKS, TIME));
        Report newer = reports.save(report(Category.ELECTRICAL, TIME.plusHours(1)));

        assertThat(reports.findAll()).extracting(Report::getReportId)
                .containsExactly(newer.getReportId(), older.getReportId());
        assertThat(reports.findByCategory(Category.ROADWORKS)).extracting(Report::getReportId)
                .containsExactly(older.getReportId());
    }

    @Test
    void statusUpdateLeavesTheCreationTimeAlone() {
        String id = reports.save(report(Category.ROADWORKS, TIME)).getReportId();

        reports.updateStatus(id, ReportStatus.IN_PROGRESS);

        Report updated = reports.findById(id);
        assertThat(updated.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        assertThat(updated.getCreatedAt()).isEqualTo(TIME);
        assertThat(updated.getUpdatedAt()).isAfter(TIME);
    }

    @Test
    void deletesOneReport() {
        String kept = reports.save(report(Category.ROADWORKS, TIME)).getReportId();
        String removed = reports.save(report(Category.ROADWORKS, TIME)).getReportId();

        reports.delete(removed);

        assertThat(reports.findById(removed)).isNull();
        assertThat(reports.findAll()).extracting(Report::getReportId).containsExactly(kept);
    }

    @Test
    void returnsNullForUnknownOrUnusableIds() {
        assertThat(reports.findById("missing")).isNull();
        assertThat(reports.findById("")).isNull();
        assertThat(reports.findById("a/b/c")).isNull();
        assertThat(reports.findById(null)).isNull();
    }

    @Test
    void skipsAMalformedReport() throws Exception {
        firestore.collection("reports").add(Map.of("title", "broken", "category", "FLOOD",
                "createdAt", FirestoreTypes.toTimestamp(TIME))).get();
        reports.save(report(Category.SANITATION, TIME));

        assertThat(reports.findAll()).hasSize(1);
    }

    @Test
    void storesNewsAndDetectsDuplicates() {
        NewsItem item = new NewsItem();
        item.setTitle("Road Repair Advisory");
        item.setSummary("Nighttime asphalt reblocking.");
        item.setSourceName("Sample source");
        item.setSourceUrl("https://example.com/road-repair");
        item.setPublishedDate(LocalDate.of(2026, 11, 19));
        item.setFetchedAt(TIME);

        news.save(item);

        assertThat(news.existsBySourceUrl("https://example.com/road-repair")).isTrue();
        assertThat(news.existsBySourceUrl("https://example.com/other")).isFalse();
        List<NewsItem> latest = news.findLatest(10);
        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).getPublishedDate()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(latest.get(0).getImageUrl()).isNull();
    }

    @Test
    void resetTokenWorksOnceAndExpires() {
        assertThat(admins.isEmpty()).isTrue();
        Admin admin = new Admin();
        admin.setUsername("qa.admin");
        admin.setEmail("qa.admin@example.com");
        admin.setPasswordHash("old-hash");
        String id = admins.save(admin).getAdminId();
        assertThat(admins.isEmpty()).isFalse();
        assertThat(admins.findByUsername("nobody")).isNull();

        admins.saveResetToken(id, "token-hash", FirestoreTypes.now().plusMinutes(30));
        assertThat(admins.findByResetTokenHash("token-hash").getAdminId()).isEqualTo(id);
        assertThat(admins.updatePasswordHash(id, "new-hash")).isTrue();
        assertThat(admins.updatePasswordHash(id, "third-hash")).isFalse();
        assertThat(admins.findByEmail("qa.admin@example.com").getPasswordHash()).isEqualTo("new-hash");
        assertThat(admins.findByUsername("qa.admin").getResetTokenHash()).isNull();

        admins.saveResetToken(id, "late-hash", FirestoreTypes.now().minusMinutes(1));
        assertThat(admins.updatePasswordHash(id, "fourth-hash")).isFalse();
    }
}
