/*
 * File:        FirebaseConfigTest.java
 * Module:      Testing
 * Purpose:     Checks that the site still starts when Firestore does not answer.
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;

/** Tests for FirebaseConfig. */
class FirebaseConfigTest {

    @Test
    void startsWhenFirestoreDoesNotAnswer() {
        Firestore firestore = mock(Firestore.class, RETURNS_DEEP_STUBS);
        when(firestore.collection("admins").limit(1).get())
                .thenReturn(ApiFutures.immediateFailedFuture(new IllegalStateException("down")));

        assertThatCode(() -> FirebaseConfig.warmUp(firestore)).doesNotThrowAnyException();
    }
}
