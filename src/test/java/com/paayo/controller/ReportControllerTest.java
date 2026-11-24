/*
 * File:        ReportControllerTest.java
 * Module:      Testing
 * Purpose:     Checks the public pages and the submission form (TC-01 to TC-04, TC-14, TC-16).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paayo.config.SecurityConfig;
import com.paayo.exception.PersistenceException;
import com.paayo.exception.ValidationException;
import com.paayo.model.Category;
import com.paayo.model.NewsItem;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.service.NewsService;
import com.paayo.service.ReportService;
import com.paayo.testutil.Photos;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Tests for ReportController with real templates and security, and mocked services. */
@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private NewsService newsService;

    private static Report report(String title, String imageUrl) {
        Report report = new Report();
        report.setReportId("id1");
        report.setTitle(title);
        report.setCategory(Category.ROADWORKS);
        report.setLocation("A.S. Fortuna St.");
        report.setDescription("Deep pothole");
        report.setImageUrl(imageUrl);
        report.setStatus(ReportStatus.IN_PROGRESS);
        report.setCreatedAt(LocalDateTime.of(2026, 11, 19, 9, 0));
        return report;
    }

    private static MockMultipartHttpServletRequestBuilder form(String title, String category, String location,
                                                               String description) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/reports/new").param("title", title)
                .param("category", category).param("location", location).param("description", description);
    }

    private static MockMultipartHttpServletRequestBuilder validForm() {
        return form("Sidewalk Garbage Pile", "SANITATION", "Plaridel St.", "Uncollected garbage");
    }

    private static NewsItem newsItem(String title) {
        NewsItem item = new NewsItem();
        item.setTitle(title);
        item.setSummary("Reblocking along M.C. Briones St.");
        item.setSourceName("Sample source");
        item.setSourceUrl("https://example.com/news");
        item.setPublishedDate(LocalDate.of(2026, 11, 18));
        return item;
    }

    @Test
    void showsReportsAndNewsWithPhotosOrPlaceholders() throws Exception {
        NewsItem item = newsItem("Road Repair Advisory");
        when(reportService.getAllReports()).thenReturn(List.of(
                report("With photo", "https://example.com/p.jpg"), report("Without photo", null)));
        when(newsService.getLatestNews(10)).thenReturn(List.of(item));

        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.allOf(
                        containsString("With photo"), containsString("https://example.com/p.jpg"),
                        containsString("/images/report-placeholder.png"), containsString("In Progress"),
                        containsString("Roadworks"), containsString("Nov 19, 2026"),
                        containsString("Road Repair Advisory"), containsString("/images/news-placeholder.png"),
                        containsString("https://example.com/news"), containsString("Nov 18, 2026"))));
    }

    @Test
    void showsTheNewestFourNewsItemsAsHotNewsAndTheRestInTheSidebar() throws Exception {
        when(newsService.getLatestNews(10)).thenReturn(Stream.of("N1", "N2", "N3", "N4", "N5", "N6")
                .map(ReportControllerTest::newsItem).toList());

        String page = mvc.perform(get("/").param("category", "ROADWORKS"))
                .andReturn().getResponse().getContentAsString();
        Document html = Jsoup.parse(page);

        assertThat(html.select(".hot-card h3").eachText()).containsExactly("N1", "N2", "N3", "N4");
        assertThat(html.select(".hot-card .badge").eachText()).containsOnly("Hot News");
        assertThat(html.select(".news-item h3").eachText()).containsExactly("N5", "N6");
    }

    @Test
    void hidesHotNewsWhenThereIsNone() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(not(containsString("Hot News"))))
                .andExpect(content().string(containsString("No other news yet.")));
    }

    /** TC-04: the filter shows one category. */
    @Test
    void filtersByCategory() throws Exception {
        when(reportService.getReportsByCategory(Category.ROADWORKS)).thenReturn(List.of(report("Pothole", null)));

        mvc.perform(get("/").param("category", "ROADWORKS"))
                .andExpect(model().attribute("selected", Category.ROADWORKS))
                .andExpect(content().string(containsString("Pothole")));
        verify(reportService, never()).getAllReports();
    }

    /** TC-04: an unknown category falls back to all reports. */
    @Test
    void unknownCategoryShowsEverything() throws Exception {
        mvc.perform(get("/").param("category", "INVALID")).andExpect(status().isOk());

        verify(reportService).getAllReports();
    }

    /** UC-02 E3: no stack trace when Firestore is down. */
    @Test
    void showsAFriendlyPanelWhenFirestoreFails() throws Exception {
        when(reportService.getAllReports()).thenThrow(new PersistenceException("secret detail", null));
        when(newsService.getLatestNews(10)).thenThrow(new PersistenceException("secret detail", null));

        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Reports are unavailable right now")))
                .andExpect(content().string(containsString("News is unavailable right now")))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    void formShowsThePrivacyNoticeAndTheThreeCategories() throws Exception {
        mvc.perform(get("/reports/new")).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.allOf(
                        containsString("Prototype System: Do not enter sensitive personal information."),
                        containsString("value=\"SANITATION\""), containsString("value=\"ROADWORKS\""),
                        containsString("value=\"ELECTRICAL\""), containsString("name=\"_csrf\""))));
    }

    /** TC-01: a valid submission redirects to the dashboard. */
    @Test
    void submitsAValidReport() throws Exception {
        mvc.perform(validForm().file(Photos.of("image/jpeg", 100)).with(csrf()))
                .andExpect(redirectedUrl("/")).andExpect(flash().attributeExists("message"));

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportService).submitReport(saved.capture(), any());
        assertThat(saved.getValue().getCategory()).isEqualTo(Category.SANITATION);
    }

    /** TC-02 and TC-03: empty, blank or unknown values show inline errors and save nothing. */
    @Test
    void showsInlineErrorsForMissingFields() throws Exception {
        mvc.perform(form("", "FLOOD", "   ", "").with(csrf())).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("report", "title", "category", "location", "description"))
                .andExpect(content().string(containsString("Enter a report title.")))
                .andExpect(content().string(containsString("Select a category.")));
        verify(reportService, never()).submitReport(any(), any());
    }

    /** TC-03: values over the limits show inline errors. */
    @Test
    void showsInlineErrorsForLongFields() throws Exception {
        mvc.perform(form("t".repeat(101), "SANITATION", "l".repeat(201), "d".repeat(1001)).with(csrf()))
                .andExpect(model().attributeHasFieldErrors("report", "title", "location", "description"))
                .andExpect(content().string(containsString("Keep the title within 100 characters.")));
        verify(reportService, never()).submitReport(any(), any());
    }

    @Test
    void lineBreaksCountAsOneCharacter() throws Exception {
        mvc.perform(form("Title", "SANITATION", "Place", "d".repeat(499) + "\r\n" + "d".repeat(500)).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    /** DDS 8.3: visitors cannot set protected fields. */
    @Test
    void ignoresProtectedFields() throws Exception {
        mvc.perform(validForm().param("status", "RESOLVED").param("imageUrl", "https://evil.example")
                .param("reportId", "chosen").with(csrf())).andExpect(redirectedUrl("/"));

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportService).submitReport(saved.capture(), any());
        assertThat(saved.getValue().getStatus()).isNull();
        assertThat(saved.getValue().getImageUrl()).isNull();
        assertThat(saved.getValue().getReportId()).isNull();
    }

    /** TC-16c: a rejected photo shows its error next to the field. */
    @Test
    void showsThePhotoError() throws Exception {
        when(reportService.submitReport(any(), any())).thenThrow(new ValidationException("The photo must be smaller."));

        mvc.perform(validForm().with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("The photo must be smaller.")));
    }

    /** UC-01 E2: a storage failure keeps the entered text. */
    @Test
    void keepsTheTextWhenSavingFails() throws Exception {
        when(reportService.submitReport(any(), any())).thenThrow(new PersistenceException("timeout", null));

        mvc.perform(validForm().with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Unable to save your report right now. Please try again.")))
                .andExpect(content().string(containsString("Sidewalk Garbage Pile")));
    }

    /** TC-14: script tags are shown as text. */
    @Test
    void escapesScriptTags() throws Exception {
        when(reportService.getAllReports()).thenReturn(List.of(report("<script>alert(1)</script>", null)));

        mvc.perform(get("/")).andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    /** TC-14: a POST without a CSRF token is rejected. */
    @Test
    void rejectsAPostWithoutACsrfToken() throws Exception {
        mvc.perform(validForm()).andExpect(status().isForbidden());

        verify(reportService, never()).submitReport(any(), any());
    }
}
