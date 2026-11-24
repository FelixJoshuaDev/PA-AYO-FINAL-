/*
 * File:        ReportTest.java
 * Module:      Testing
 * Purpose:     Checks report field limits and status changes (FR-05, FR-08, TC-02, TC-03).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Tests for Report. */
class ReportTest {

    private static Report report(String title, Category category, String location, String description) {
        Report report = new Report();
        report.setTitle(title);
        report.setCategory(category);
        report.setLocation(location);
        report.setDescription(description);
        return report;
    }

    @Test
    void acceptsTheLongestAllowedValuesAfterTrimming() {
        assertThat(report(" " + "t".repeat(100) + " ", Category.SANITATION, "l".repeat(200), "d".repeat(1000)).isValid())
                .isTrue();
    }

    @Test
    void rejectsValuesOverTheLimit() {
        assertThat(report("t".repeat(101), Category.SANITATION, "l", "d").isValid()).isFalse();
        assertThat(report("t", Category.SANITATION, "l".repeat(201), "d").isValid()).isFalse();
        assertThat(report("t", Category.SANITATION, "l", "d".repeat(1001)).isValid()).isFalse();
    }

    @Test
    void rejectsMissingOrBlankFields() {
        assertThat(report(null, Category.SANITATION, "l", "d").isValid()).isFalse();
        assertThat(report("t", null, "l", "d").isValid()).isFalse();
        assertThat(report("t", Category.SANITATION, "   ", "d").isValid()).isFalse();
        assertThat(report("t", Category.SANITATION, "l", "").isValid()).isFalse();
    }

    @Test
    void statusChangeUpdatesTheTimestampButNotTheCreationTime() {
        Report report = report("t", Category.SANITATION, "l", "d");
        LocalDateTime created = LocalDateTime.of(2026, 11, 1, 8, 0);
        report.setCreatedAt(created);
        report.setUpdatedAt(created);

        report.updateStatus(ReportStatus.RESOLVED);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getCreatedAt()).isEqualTo(created);
        assertThat(report.getUpdatedAt()).isAfter(created);
    }
}
