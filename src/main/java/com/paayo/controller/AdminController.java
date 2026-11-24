/*
 * File:        AdminController.java
 * Module:      Admin pages
 * Purpose:     Serves admin login, password recovery and the report management dashboard (UC-03, UC-07).
 * Responsible: Felix Joshua H. Abing
 * Created:     2026-11-24
 */
package com.paayo.controller;

import com.paayo.exception.NotFoundException;
import com.paayo.exception.PersistenceException;
import com.paayo.exception.ValidationException;
import com.paayo.model.ReportStatus;
import com.paayo.service.AdminAuthService;
import com.paayo.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Administrator pages. SecurityConfig decides which of them need a login. */
@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final String LOGIN = "admin/login";
    private static final String FORGOT_PASSWORD = "admin/forgot-password";
    private static final String RESET_PASSWORD = "admin/reset-password";
    private static final String TO_DASHBOARD = "redirect:/admin/dashboard";

    private final ReportService reportService;
    private final AdminAuthService adminAuthService;
    private final SecurityContextRepository securityContextRepository;

    /** Uses the report and authentication services and the login session store. */
    public AdminController(ReportService reportService, AdminAuthService adminAuthService,
                           SecurityContextRepository securityContextRepository) {
        this.reportService = reportService;
        this.adminAuthService = adminAuthService;
        this.securityContextRepository = securityContextRepository;
    }

    /** Shows the login page. */
    @GetMapping("/login")
    public String showLogin() {
        return LOGIN;
    }

    /** Starts an admin session, or shows the same generic error for any wrong username or password. */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, Model model,
                        HttpServletRequest request, HttpServletResponse response) {
        if (!adminAuthService.authenticate(username, password)) {
            model.addAttribute("error", "Invalid username or password");
            return LOGIN;
        }
        // A fresh session ID stops an attacker from reusing one planted before login.
        request.getSession();
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                AdminAuthService.normalize(username), null, List.of()));
        securityContextRepository.saveContext(context, request, response);
        return TO_DASHBOARD;
    }

    /** Shows the password recovery request page. */
    @GetMapping("/forgot-password")
    public String showForgotPassword() {
        return FORGOT_PASSWORD;
    }

    /** Starts a password reset and always answers with the same notice. */
    @PostMapping("/forgot-password")
    public String requestPasswordReset(@RequestParam String identifier, Model model) {
        adminAuthService.initiatePasswordReset(identifier);
        model.addAttribute("message",
                "If an admin account matches your entry, a password reset link has been sent.");
        return FORGOT_PASSWORD;
    }

    /** Shows the new-password form, or an error when the link is invalid, used or expired. */
    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam(defaultValue = "") String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("validToken", adminAuthService.validateResetToken(token));
        return RESET_PASSWORD;
    }

    /** Saves the new password and returns to the login page. */
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token, @RequestParam String newPassword,
                                @RequestParam String confirmPassword, Model model, RedirectAttributes redirect) {
        if (!AdminAuthService.isAcceptablePassword(newPassword)) {
            model.addAttribute("error", "Use at least 8 characters (and at most 72).");
            return showResetPassword(token, model);
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "The two passwords do not match.");
            return showResetPassword(token, model);
        }
        if (!adminAuthService.resetPassword(token, newPassword)) {
            model.addAttribute("validToken", false);
            return RESET_PASSWORD;
        }
        redirect.addFlashAttribute("message", "Your password was changed. You can sign in now.");
        return "redirect:/admin/login";
    }

    /** Shows every report, newest first, with its status controls. */
    @GetMapping("/dashboard")
    public String showAdminPanel(Model model) {
        model.addAttribute("statuses", ReportStatus.values());
        try {
            model.addAttribute("reports", reportService.getAllReports());
        } catch (PersistenceException e) {
            log.error("Reports could not be loaded", e);
        }
        return "admin/dashboard";
    }

    /** Changes the status of one report. */
    @PostMapping("/updateStatus")
    public String updateStatus(@RequestParam String reportId, @RequestParam ReportStatus status,
                               RedirectAttributes redirect) {
        try {
            reportService.updateReportStatus(reportId, status);
            redirect.addFlashAttribute("message", "Status updated successfully");
        } catch (NotFoundException | ValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (PersistenceException e) {
            log.error("Status could not be updated", e);
            redirect.addFlashAttribute("error", "Unable to update the status right now. Please try again.");
        }
        return TO_DASHBOARD;
    }
}
