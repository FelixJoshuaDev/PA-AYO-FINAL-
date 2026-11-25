/*
 * File:        ReportController.java
 * Module:      Reports
 * Purpose:     Serves the public dashboard and the report submission form (UC-01, UC-02).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.controller;

import com.paayo.exception.PersistenceException;
import com.paayo.exception.ValidationException;
import com.paayo.model.Category;
import com.paayo.model.Report;
import com.paayo.service.NewsService;
import com.paayo.service.ReportService;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Public pages. */
@Controller
public class ReportController {
    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final int NEWS_LIMIT = 10;

    private final ReportService reportService;
    private final NewsService newsService;

    /** Uses the report and news services. */
    public ReportController(ReportService reportService, NewsService newsService) {
        this.reportService = reportService;
        this.newsService = newsService;
    }

    /** Limits what visitors can submit, trims text and turns an unknown category into "none". */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("title", "category", "location", "description");
        // Dropping \r makes a line break count as one character, as it does in the browser's maxlength.
        binder.registerCustomEditor(String.class, new StringTrimmerEditor("\r", false));
        binder.registerCustomEditor(Category.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                try {
                    setValue(Category.valueOf(text));
                } catch (IllegalArgumentException e) {
                    setValue(null);
                }
            }
        });
    }

    /** Shows every report, newest first, next to the latest news. */
    @GetMapping("/")
    public String showDashboard(Model model) {
        return dashboard(null, model);
    }

    /** Shows the reports of one category; an unknown category shows all of them. */
    @GetMapping(path = "/", params = "category")
    public String filterByCategory(@RequestParam(required = false) Category category, Model model) {
        return dashboard(category, model);
    }

    /** Shows the empty submission form. */
    @GetMapping("/reports/new")
    public String showReportForm(Model model) {
        model.addAttribute("report", new Report());
        return form(model);
    }

    /** Saves a valid report and returns to the dashboard; otherwise shows the form again with errors. */
    @PostMapping("/reports/new")
    public String submitReport(@Valid @ModelAttribute Report report, BindingResult result,
                               @RequestParam(required = false) MultipartFile photoFile,
                               Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return form(model);
        }
        try {
            reportService.submitReport(report, photoFile);
        } catch (ValidationException e) {
            model.addAttribute("photoError", e.getMessage());
            return form(model);
        } catch (PersistenceException e) {
            log.error("Report could not be saved", e);
            model.addAttribute("saveError", "Unable to save your report right now. Please try again.");
            return form(model);
        }
        redirect.addFlashAttribute("message", "Your report was submitted. Thank you for helping your community!");
        return "redirect:/";
    }

    /** Shows the form with a photo error when the upload is over the server limit. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleOversizedUpload(Model model) {
        model.addAttribute("report", new Report());
        model.addAttribute("photoError", "The photo must be 5 MB or smaller.");
        return form(model);
    }

    private String dashboard(Category category, Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("selected", category);
        try {
            model.addAttribute("reports", category == null
                    ? reportService.getAllReports() : reportService.getReportsByCategory(category));
        } catch (PersistenceException e) {
            log.error("Reports could not be loaded", e);
        }
        try {
            model.addAttribute("news", newsService.getLatestNews(NEWS_LIMIT));
        } catch (PersistenceException e) {
            log.error("News could not be loaded", e);
        }
        return "dashboard";
    }

    private static String form(Model model) {
        model.addAttribute("categories", Category.values());
        return "report-form";
    }
}
