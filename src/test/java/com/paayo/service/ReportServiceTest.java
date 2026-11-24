/*
 * File:        ReportServiceTest.java
 * Module:      Testing
 * Purpose:     Checks report submission, listing and status rules (TC-01, TC-03, TC-05, TC-16).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.paayo.exception.NotFoundException;
import com.paayo.exception.PersistenceException;
import com.paayo.exception.ValidationException;
import com.paayo.model.Category;
import com.paayo.model.Report;
import com.paayo.model.ReportStatus;
import com.paayo.repository.ReportRepository;
import com.paayo.testutil.Photos;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

/** Tests for ReportService. */
class ReportServiceTest {
    private final ReportRepository repository = mock(ReportRepository.class);
    private final ImageStorageService images = mock(ImageStorageService.class);
    private final ReportService service = new ReportService(repository, images);

    private static Report report() {
        Report report = new Report();
        report.setTitle(" Sidewalk Garbage Pile ");
        report.setCategory(Category.SANITATION);
        report.setLocation(" Plaridel St., Alang-Alang, Mandaue ");
        report.setDescription(" Uncollected garbage pile blocking sidewalk ");
        return report;
    }

    /** TC-01: a valid report without a photo. */
    @Test
    void savesAValidReportAsReported() {
        when(repository.save(any())).then(returnsFirstArg());

        Report saved = service.submitReport(report(), null);

        assertThat(saved.getTitle()).isEqualTo("Sidewalk Garbage Pile");
        assertThat(saved.getLocation()).isEqualTo("Plaridel St., Alang-Alang, Mandaue");
        assertThat(saved.getDescription()).isEqualTo("Uncollected garbage pile blocking sidewalk");
        assertThat(saved.getImageUrl()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.REPORTED);
        assertThat(saved.getCreatedAt()).isNotNull().isEqualTo(saved.getUpdatedAt());
    }

    /** TC-16a: a valid report with a photo. */
    @Test
    void savesThePhotoUrl() {
        MultipartFile photo = Photos.of("image/jpeg", 1024 * 1024);
        when(images.storePhoto(photo)).thenReturn("https://example.com/p.jpg");
        when(repository.save(any())).then(returnsFirstArg());

        assertThat(service.submitReport(report(), photo).getImageUrl()).isEqualTo("https://example.com/p.jpg");
    }

    /** TC-03: invalid fields never reach Firestore or photo storage. */
    @Test
    void storesNothingWhenAFieldIsInvalid() {
        Report invalid = report();
        invalid.setTitle("t".repeat(101));

        assertThatThrownBy(() -> service.submitReport(invalid, null)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(repository, images);
    }

    /** TC-16c: a rejected photo stores nothing. */
    @Test
    void storesNothingWhenThePhotoIsInvalid() {
        MultipartFile photo = Photos.of("image/jpeg", 10);
        doThrow(new ValidationException("bad photo")).when(images).validatePhoto(photo);

        assertThatThrownBy(() -> service.submitReport(report(), photo)).hasMessage("bad photo");
        verify(images, never()).storePhoto(any());
        verifyNoInteractions(repository);
    }

    @Test
    void passesOnAFirestoreFailure() {
        when(repository.save(any())).thenThrow(new PersistenceException("timeout", null));

        assertThatThrownBy(() -> service.submitReport(report(), null)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void listsReports() {
        List<Report> all = List.of(report());
        when(repository.findAll()).thenReturn(all);
        when(repository.findByCategory(Category.ROADWORKS)).thenReturn(List.of());

        assertThat(service.getAllReports()).isEqualTo(all);
        assertThat(service.getReportsByCategory(Category.ROADWORKS)).isEmpty();
    }

    /** TC-05: status changes go through the model and the repository. */
    @Test
    void updatesTheStatus() {
        Report report = report();
        when(repository.findById("id1")).thenReturn(report);

        service.updateReportStatus("id1", ReportStatus.IN_PROGRESS);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.IN_PROGRESS);
        verify(repository).updateStatus("id1", ReportStatus.IN_PROGRESS);
    }

    @Test
    void refusesAMissingStatusOrReport() {
        assertThatThrownBy(() -> service.updateReportStatus("id1", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.updateReportStatus("gone", ReportStatus.RESOLVED))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).updateStatus(any(), any());
    }

    @Test
    void deletesAReportAndItsPhoto() {
        Report report = report();
        report.setImageUrl("https://example.com/pa-ayo/p.jpg");
        when(repository.findById("id1")).thenReturn(report);

        service.deleteReport("id1");

        verify(repository).delete("id1");
        verify(images).deletePhoto("https://example.com/pa-ayo/p.jpg");
    }

    @Test
    void cannotDeleteAMissingReport() {
        assertThatThrownBy(() -> service.deleteReport("gone")).isInstanceOf(NotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void keepsThePhotoWhenTheReportCannotBeDeleted() {
        when(repository.findById("id1")).thenReturn(report());
        doThrow(new PersistenceException("down", null)).when(repository).delete("id1");

        assertThatThrownBy(() -> service.deleteReport("id1")).isInstanceOf(PersistenceException.class);
        verifyNoInteractions(images);
    }

    @Test
    void validatesReports() {
        assertThat(service.validateReport(report())).isTrue();
        assertThat(service.validateReport(null)).isFalse();
    }
}
