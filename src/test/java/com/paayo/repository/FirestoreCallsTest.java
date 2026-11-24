/*
 * File:        FirestoreCallsTest.java
 * Module:      Testing
 * Purpose:     Checks that Firestore failures become PersistenceException (NFR-02).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.paayo.exception.PersistenceException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/** Tests for FirestoreCalls. */
class FirestoreCallsTest {

    @Test
    void returnsTheResult() {
        assertThat(FirestoreCalls.await(ApiFutures.immediateFuture("ok"))).isEqualTo("ok");
    }

    @Test
    void wrapsFailures() {
        assertThatThrownBy(() -> FirestoreCalls.await(ApiFutures.immediateFailedFuture(new IllegalStateException())))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void givesUpAndCancelsAfterFiveSeconds() throws Exception {
        ApiFuture<String> slow = mock(ApiFuture.class);
        when(slow.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException());

        assertThatThrownBy(() -> FirestoreCalls.await(slow)).isInstanceOf(PersistenceException.class);
        verify(slow).cancel(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoresTheInterruptFlag() throws Exception {
        ApiFuture<String> interrupted = mock(ApiFuture.class);
        when(interrupted.get(eq(5L), any())).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> FirestoreCalls.await(interrupted)).isInstanceOf(PersistenceException.class);
        assertThat(Thread.interrupted()).isTrue();
    }
}
