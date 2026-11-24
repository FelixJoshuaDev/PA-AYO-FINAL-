/*
 * File:        NewsService.java
 * Module:      News bot
 * Purpose:     Provides the latest government news for the dashboard (FR-07).
 * Responsible: Felix Joshua H. Abing
 * Created:     2026-11-24
 */
package com.paayo.service;

import com.paayo.model.NewsItem;
import com.paayo.repository.NewsRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** News business rules. */
@Service
public class NewsService {
    private final NewsRepository newsRepository;

    /** Uses the news repository. */
    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    /** Returns the most recently fetched news items. */
    public List<NewsItem> getLatestNews(int limit) {
        return newsRepository.findLatest(limit);
    }
}
