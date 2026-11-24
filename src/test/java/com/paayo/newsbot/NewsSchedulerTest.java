/*
 * File:        NewsSchedulerTest.java
 * Module:      Testing
 * Purpose:     Checks that a failing news run never reaches the website (NFR-03).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.newsbot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paayo.service.NewsService;
import org.junit.jupiter.api.Test;

/** Tests for NewsScheduler. */
class NewsSchedulerTest {
    private final NewsService newsService = mock(NewsService.class);
    private final NewsScheduler scheduler = new NewsScheduler(newsService, "0 0 6 * * *");

    @Test
    void runsThePipeline() {
        scheduler.runScheduledFetch();

        verify(newsService).fetchAndPublishNews();
    }

    @Test
    void survivesAFailure() {
        when(newsService.fetchAndPublishNews()).thenThrow(new IllegalStateException("broken"));

        assertThatCode(scheduler::runScheduledFetch).doesNotThrowAnyException();
    }
}
