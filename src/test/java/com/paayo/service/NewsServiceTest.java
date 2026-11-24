/*
 * File:        NewsServiceTest.java
 * Module:      Testing
 * Purpose:     Checks the news pipeline: publishing, duplicates and failures (TC-07 to TC-09).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paayo.exception.PersistenceException;
import com.paayo.model.NewsItem;
import com.paayo.newsbot.NewsScraper;
import com.paayo.newsbot.TextParaphraser;
import com.paayo.repository.NewsRepository;
import com.paayo.util.FirestoreTypes;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Tests for NewsService. */
class NewsServiceTest {
    private final NewsRepository repository = mock(NewsRepository.class);
    private final NewsScraper scraper = mock(NewsScraper.class);
    private final NewsService service = new NewsService(repository, scraper, new TextParaphraser(300));

    private static NewsItem item(String title, String url, String text, LocalDate date) {
        NewsItem item = new NewsItem();
        item.setTitle(title);
        item.setSourceUrl(url);
        item.setSummary(text);
        item.setSourceName("Sample source");
        item.setPublishedDate(date);
        return item;
    }

    private static NewsItem item(String url) {
        return item("Road news", url, "A road reopened.", LocalDate.of(2026, 11, 19));
    }

    /** TC-07: at least two items are stored with short summaries. */
    @Test
    void publishesNewItems() {
        NewsItem longText = item("Flood news", "https://a/2", "A sentence. ".repeat(100), LocalDate.of(2026, 11, 18));
        when(scraper.scrape()).thenReturn(List.of(item("https://a/1"), longText));

        assertThat(service.fetchAndPublishNews()).isEqualTo(2);
        assertThat(longText.getSummary()).hasSizeLessThanOrEqualTo(300).isNotBlank();
        assertThat(longText.getFetchedAt()).isNotNull();
        verify(repository, times(2)).save(any());
    }

    /** TC-08: a second run, or the same article twice in one run, adds nothing. */
    @Test
    void neverPublishesTheSameUrlTwice() {
        Set<String> stored = new HashSet<>();
        when(repository.existsBySourceUrl(anyString())).thenAnswer(call -> stored.contains(call.<String>getArgument(0)));
        when(repository.save(any())).thenAnswer(call -> {
            NewsItem saved = call.getArgument(0);
            stored.add(saved.getSourceUrl());
            return saved;
        });
        when(scraper.scrape()).thenAnswer(call -> List.of(item("https://a/1"), item("https://a/1"), item("https://a/2")));

        assertThat(service.fetchAndPublishNews()).isEqualTo(2);
        assertThat(service.fetchAndPublishNews()).isZero();
        assertThat(service.isDuplicate("https://a/1")).isTrue();
    }

    /** TC-09: a missing date becomes today in Manila. */
    @Test
    void usesTodayWhenTheDateIsMissing() {
        NewsItem undated = item("Road news", "https://a/1", "A road reopened.", null);
        when(scraper.scrape()).thenReturn(List.of(undated));

        service.fetchAndPublishNews();

        assertThat(undated.getPublishedDate()).isEqualTo(FirestoreTypes.today());
    }

    @Test
    void savesOldestFirstSoTheNewestIsOnTop() {
        NewsItem newer = item("Newer", "https://a/1", "Text.", LocalDate.of(2026, 11, 19));
        NewsItem older = item("Older", "https://a/2", "Text.", LocalDate.of(2026, 11, 1));
        when(scraper.scrape()).thenReturn(List.of(newer, older));

        service.fetchAndPublishNews();

        InOrder order = inOrder(repository);
        order.verify(repository).save(older);
        order.verify(repository).save(newer);
    }

    @Test
    void skipsItemsWithoutTitleOrUrl() {
        when(scraper.scrape()).thenReturn(List.of(
                item(" ", "https://a/1", "Text.", null), item("Title", null, "Text.", null)));

        assertThat(service.fetchAndPublishNews()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void readsTheArticlePageWhenTheListingHasNoText() {
        NewsItem noText = item("Road news", "https://a/1", "", null);
        when(scraper.scrape()).thenReturn(List.of(noText));
        when(scraper.fetchArticleText("https://a/1")).thenReturn("The road is open.");

        assertThat(service.fetchAndPublishNews()).isEqualTo(1);
        assertThat(noText.getSummary()).isEqualTo("The road is open.");
    }

    @Test
    void leavesAnArticleWithoutTextForTheNextRun() {
        when(scraper.scrape()).thenReturn(List.of(item("Road news", "https://a/1", "", null)));
        when(scraper.fetchArticleText("https://a/1")).thenReturn("");

        assertThat(service.fetchAndPublishNews()).isZero();
        verify(repository, never()).save(any());
    }

    /** UC-06 E1: one failed write does not stop the others and is not counted. */
    @Test
    void continuesAfterAFailedWrite() {
        when(scraper.scrape()).thenReturn(List.of(item("https://a/1"), item("https://a/2")));
        when(repository.save(any())).thenThrow(new PersistenceException("down", null)).thenReturn(null);

        assertThat(service.fetchAndPublishNews()).isEqualTo(1);
    }

    /** UC-04 E2: no source answered. */
    @Test
    void publishesNothingWhenEverySourceFails() {
        when(scraper.scrape()).thenReturn(List.of());

        assertThat(service.fetchAndPublishNews()).isZero();
    }

    @Test
    void returnsTheLatestNews() {
        List<NewsItem> latest = List.of(item("https://a/1"));
        when(repository.findLatest(10)).thenReturn(latest);

        assertThat(service.getLatestNews(10)).isEqualTo(latest);
    }
}
