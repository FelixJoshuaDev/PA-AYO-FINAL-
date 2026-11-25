/*
 * File:        AdminControllerTest.java
 * Module:      Testing
 * Purpose:     Checks admin access rules, login, recovery and status updates (TC-05, TC-06, TC-17).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paayo.config.SecurityConfig;
import com.paayo.exception.NotFoundException;
import com.paayo.exception.PersistenceException;
import com.paayo.model.Category;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.service.AdminAuthService;
import com.paayo.service.NewsService;
import com.paayo.service.ReportService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Tests for AdminController with real templates and security, and mocked services. */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {
    private static final String NOTICE = "If an admin account matches your entry, a password reset link has been sent.";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private NewsService newsService;
    @MockitoBean
    private AdminAuthService adminAuthService;

    private static Report report() {
        Report report = new Report();
        report.setReportId("id1");
        report.setTitle("Clogged Storm Drain");
        report.setCategory(Category.SANITATION);
        report.setLocation("A.S. Fortuna St., Banilad");
        report.setDescription("Debris blocking the drain");
        report.setStatus(ReportStatus.IN_PROGRESS);
        report.setCreatedAt(LocalDateTime.of(2026, 11, 13, 8, 0));
        return report;
    }

    /** TC-06: signed-out requests go to the login page without side effects, with or without a CSRF token. */
    @Test
    void protectsAdminRoutes() throws Exception {
        mvc.perform(get("/admin/dashboard")).andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(post("/admin/updateStatus").param("reportId", "id1").param("status", "RESOLVED"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(post("/admin/updateStatus").param("reportId", "id1").param("status", "RESOLVED").with(csrf()))
                .andExpect(status().isFound()).andExpect(redirectedUrlPattern("**/admin/login"));

        verifyNoInteractions(reportService);
    }

    @Test
    void keepsLoginAndRecoveryPublic() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Forgot Password?")));
        mvc.perform(get("/admin/forgot-password")).andExpect(status().isOk());
        mvc.perform(get("/admin/reset-password")).andExpect(status().isOk());
    }

    @Test
    void startsASessionForTheRightPassword() throws Exception {
        when(adminAuthService.authenticate("banilad.admin", "correct")).thenReturn(true);

        mvc.perform(post("/admin/login").param("username", "banilad.admin").param("password", "correct").with(csrf()))
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(authenticated().withUsername("banilad.admin"));
    }

    /** TC-06: a wrong password shows a generic error and starts no session. */
    @Test
    void refusesTheWrongPassword() throws Exception {
        mvc.perform(post("/admin/login").param("username", "banilad.admin").param("password", "wrong").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid username or password")))
                .andExpect(unauthenticated());
    }

    @Test
    void logsOut() throws Exception {
        mvc.perform(post("/admin/logout").with(user("admin")).with(csrf()))
                .andExpect(redirectedUrl("/")).andExpect(unauthenticated());
    }

    @Test
    void listsReportsWithTheirCurrentStatus() throws Exception {
        when(reportService.getAllReports()).thenReturn(List.of(report()));

        mvc.perform(get("/admin/dashboard").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Clogged Storm Drain"),
                        containsString("Nov 13, 2026"), containsString("A.S. Fortuna St., Banilad"),
                        containsString("value=\"IN_PROGRESS\" selected=\"selected\""))));
    }

    @Test
    void showsAFriendlyPanelWhenFirestoreFails() throws Exception {
        when(reportService.getAllReports()).thenThrow(new PersistenceException("secret detail", null));

        mvc.perform(get("/admin/dashboard").with(user("admin"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Reports are unavailable right now")))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    /** TC-05: an admin changes a status. */
    @Test
    void updatesAStatus() throws Exception {
        mvc.perform(post("/admin/updateStatus").param("reportId", "id1").param("status", "RESOLVED")
                        .with(user("admin")).with(csrf()))
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(flash().attribute("message", "Status updated successfully"));

        verify(reportService).updateReportStatus("id1", ReportStatus.RESOLVED);
    }

    /** UC-03 E3: an unknown report shows an error. */
    @Test
    void explainsWhyAStatusDidNotChange() throws Exception {
        doThrow(new NotFoundException("That report no longer exists."))
                .doThrow(new PersistenceException("timeout", null))
                .when(reportService).updateReportStatus(anyString(), any());

        mvc.perform(post("/admin/updateStatus").param("reportId", "gone").param("status", "RESOLVED")
                .with(user("admin")).with(csrf())).andExpect(flash().attribute("error", "That report no longer exists."));
        mvc.perform(post("/admin/updateStatus").param("reportId", "id1").param("status", "RESOLVED")
                .with(user("admin")).with(csrf())).andExpect(flash().attribute("error", containsString("try again")));
    }

    @Test
    void deletesAReport() throws Exception {
        mvc.perform(post("/admin/deleteReport").param("reportId", "id1").with(user("admin")).with(csrf()))
                .andExpect(redirectedUrl("/admin/dashboard")).andExpect(flash().attribute("message", "Report deleted"));

        verify(reportService).deleteReport("id1");
    }

    @Test
    void explainsWhyAReportWasNotDeleted() throws Exception {
        doThrow(new NotFoundException("That report no longer exists.")).doThrow(new PersistenceException("down", null))
                .when(reportService).deleteReport("id1");

        mvc.perform(post("/admin/deleteReport").param("reportId", "id1").with(user("admin")).with(csrf()))
                .andExpect(flash().attribute("error", "That report no longer exists."));
        mvc.perform(post("/admin/deleteReport").param("reportId", "id1").with(user("admin")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("try again")));
    }

    @Test
    void onlyAdminsCanUseTheAdminDelete() throws Exception {
        mvc.perform(post("/admin/deleteReport").param("reportId", "id1").with(csrf()))
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(post("/admin/deleteReport").param("reportId", "id1").with(user("admin")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }

    @Test
    void refusesAnUnknownStatus() throws Exception {
        mvc.perform(post("/admin/updateStatus").param("reportId", "id1").param("status", "DELETED")
                .with(user("admin")).with(csrf())).andExpect(status().isBadRequest());

        verify(reportService, never()).updateReportStatus(any(), any());
    }

    /** TC-17a: the notice is the same for every entry. */
    @Test
    void answersEveryRecoveryRequestTheSameWay() throws Exception {
        for (String identifier : List.of("banilad.admin", "stranger")) {
            mvc.perform(post("/admin/forgot-password").param("identifier", identifier).with(csrf()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString(NOTICE)));
            verify(adminAuthService).initiatePasswordReset(identifier);
        }
    }

    /** TC-17c: an invalid link shows an error instead of the form. */
    @Test
    void showsAnErrorForAnInvalidLink() throws Exception {
        mvc.perform(get("/admin/reset-password").param("token", "old"))
                .andExpect(content().string(allOf(containsString("invalid, has expired or was already used"),
                        not(containsString("name=\"newPassword\"")))));
    }

    /** TC-17b: a valid link changes the password. */
    @Test
    void resetsThePassword() throws Exception {
        when(adminAuthService.validateResetToken("good")).thenReturn(true);
        when(adminAuthService.resetPassword("good", "new-password")).thenReturn(true);

        mvc.perform(get("/admin/reset-password").param("token", "good"))
                .andExpect(content().string(containsString("name=\"newPassword\"")));
        mvc.perform(post("/admin/reset-password").param("token", "good").param("newPassword", "new-password")
                        .param("confirmPassword", "new-password").with(csrf()))
                .andExpect(redirectedUrl("/admin/login")).andExpect(flash().attributeExists("message"));
    }

    /** UC-07 E3: a short or mismatched password shows an inline error and keeps the token. */
    @Test
    void explainsAnUnacceptablePassword() throws Exception {
        when(adminAuthService.validateResetToken("good")).thenReturn(true);

        mvc.perform(post("/admin/reset-password").param("token", "good").param("newPassword", "short")
                        .param("confirmPassword", "short").with(csrf()))
                .andExpect(content().string(containsString("Use at least 8 characters")));
        mvc.perform(post("/admin/reset-password").param("token", "good").param("newPassword", "new-password")
                        .param("confirmPassword", "different").with(csrf()))
                .andExpect(content().string(containsString("do not match")));
        verify(adminAuthService, never()).resetPassword(any(), any());
    }

    /** TC-17c: a link that expired while the form was open is rejected. */
    @Test
    void rejectsALinkThatExpiredMeanwhile() throws Exception {
        mvc.perform(post("/admin/reset-password").param("token", "old").param("newPassword", "new-password")
                        .param("confirmPassword", "new-password").with(csrf()))
                .andExpect(content().string(containsString("invalid, has expired or was already used")));
    }

    @Test
    void showsAnErrorPageStatusWhenLoginCannotReachFirestore() throws Exception {
        when(adminAuthService.authenticate(any(), any())).thenThrow(new PersistenceException("down", null));

        mvc.perform(post("/admin/login").param("username", "a").param("password", "b").with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }

    /** UC-04: an admin runs the news bot and sees the count, or a warning when nothing is new. */
    @Test
    void runsTheNewsBot() throws Exception {
        when(newsService.fetchAndPublishNews()).thenReturn(3, 0);

        mvc.perform(post("/admin/news/fetch").with(user("admin")).with(csrf()))
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andExpect(flash().attribute("message", "3 new item(s) published"));
        mvc.perform(post("/admin/news/fetch").with(user("admin")).with(csrf()))
                .andExpect(flash().attribute("warning", containsString("0 new items")));
    }

    /** UC-04 E1: a signed-out request does not run the bot. */
    @Test
    void protectsTheNewsBot() throws Exception {
        mvc.perform(post("/admin/news/fetch").with(csrf())).andExpect(redirectedUrlPattern("**/admin/login"));

        verifyNoInteractions(newsService);
    }
}
