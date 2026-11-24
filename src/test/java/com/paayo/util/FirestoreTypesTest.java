/*
 * File:        FirestoreTypesTest.java
 * Module:      Testing
 * Purpose:     Checks the Asia/Manila date conversions (NFR-16).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Tests for FirestoreTypes. */
class FirestoreTypesTest {

    @Test
    void storesManilaTimeAsUtcAndReadsItBack() {
        LocalDateTime manila = LocalDateTime.of(2026, 11, 18, 9, 15);

        Timestamp stored = FirestoreTypes.toTimestamp(manila);

        assertThat(stored.toString()).startsWith("2026-11-18T01:15:00");
        assertThat(FirestoreTypes.toLocalDateTime(stored)).isEqualTo(manila);
    }

    @Test
    void keepsNullAsNull() {
        assertThat(FirestoreTypes.toTimestamp(null)).isNull();
        assertThat(FirestoreTypes.toLocalDateTime(null)).isNull();
    }
}
