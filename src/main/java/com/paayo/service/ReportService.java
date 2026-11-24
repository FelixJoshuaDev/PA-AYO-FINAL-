/*
 * File:        ReportService.java
 * Module:      Reports
 * Purpose:     Business rules for submitting, listing and updating reports (FR-01 to FR-05, FR-08).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.service;

import com.paayo.exception.NotFoundException;
import com.paayo.exception.ValidationException;
import com.paayo.model.Category;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.repository.ReportRepository;
import com.paayo.util.FirestoreTypes;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Report business rules. */
@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final ImageStorageService imageStorageService;

    /** Uses the report repository and the photo storage. */
    public ReportService(ReportRepository reportRepository, ImageStorageService imageStorageService) {
        this.reportRepository = reportRepository;
        this.imageStorageService = imageStorageService;
    }

    /** Validates and saves a new report with its optional photo; nothing is stored if validation fails. */
    public Report submitReport(Report report, MultipartFile photoFile) {
        if (!validateReport(report)) {
            throw new ValidationException("Please check the title, category, location and description.");
        }
        imageStorageService.validatePhoto(photoFile);
        report.setTitle(report.getTitle().trim());
        report.setLocation(report.getLocation().trim());
        report.setDescription(report.getDescription().trim());
        // shortcut: the photo stays on Cloudinary if the save below fails, delete it here if orphans pile up
        report.setImageUrl(imageStorageService.storePhoto(photoFile));
        report.setStatus(ReportStatus.REPORTED);
        LocalDateTime now = FirestoreTypes.now();
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        return reportRepository.save(report);
    }

    /** Returns the newest reports. */
    public List<Report> getAllReports() {
        return reportRepository.findAll();
    }

    /** Returns the newest reports of one category. */
    public List<Report> getReportsByCategory(Category category) {
        return reportRepository.findByCategory(category);
    }

    /** Changes a report's status; the report must exist. */
    public void updateReportStatus(String id, ReportStatus status) {
        if (status == null) {
            throw new ValidationException("Select a status.");
        }
        Report report = reportRepository.findById(id);
        if (report == null) {
            throw new NotFoundException("That report no longer exists.");
        }
        report.updateStatus(status);
        reportRepository.updateStatus(id, report.getStatus());
    }

    /** Returns true when the report passes every field rule. */
    public boolean validateReport(Report report) {
        return report != null && report.isValid();
    }
}
