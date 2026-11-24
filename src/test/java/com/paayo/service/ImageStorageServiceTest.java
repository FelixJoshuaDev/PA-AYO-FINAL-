/*
 * File:        ImageStorageServiceTest.java
 * Module:      Testing
 * Purpose:     Checks photo validation and storage (FR-08, FR-12, TC-16).
 * Responsible: Reuel Matthew Arcilla
 * Created:     2026-11-24
 */
package com.paayo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.paayo.exception.PersistenceException;
import com.paayo.exception.ValidationException;
import com.paayo.testutil.Photos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Tests for ImageStorageService. */
class ImageStorageServiceTest {
    private static final int MB = 1024 * 1024;

    private final Cloudinary cloudinary = mock(Cloudinary.class);
    private final Uploader uploader = mock(Uploader.class);
    private final ImageStorageService service = new ImageStorageService(cloudinary, DataSize.ofMegabytes(5),
            List.of("image/jpeg", "image/png", "image/webp"));

    @BeforeEach
    void connectUploader() {
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    @ParameterizedTest
    @CsvSource({"image/jpeg", "image/png", "image/webp"})
    void acceptsTheThreeImageTypes(String type) {
        assertThatCode(() -> service.validatePhoto(Photos.of(type, 5 * MB))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPhotoOverFiveMegabytes() {
        assertThatThrownBy(() -> service.validatePhoto(Photos.of("image/jpeg", 5 * MB + 1)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("5 MB");
    }

    @Test
    void rejectsOtherFileTypes() {
        MultipartFile pdf = new MockMultipartFile("photoFile", "a.pdf", "application/pdf", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> service.validatePhoto(pdf))
                .isInstanceOf(ValidationException.class).hasMessageContaining("JPEG, PNG or WebP");
    }

    @Test
    void rejectsAProgramRenamedToLookLikeAPhoto() {
        MultipartFile exe = new MockMultipartFile("photoFile", "a.jpg", "image/jpeg", "MZ program".getBytes());

        assertThatThrownBy(() -> service.validatePhoto(exe)).isInstanceOf(ValidationException.class);
    }

    @Test
    void reportsAnUnreadableUpload() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getContentType()).thenReturn("image/jpeg");
        when(broken.getInputStream()).thenThrow(new IOException("disk"));

        assertThatThrownBy(() -> service.validatePhoto(broken)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void treatsAMissingOrEmptyFileAsNoPhoto() {
        MultipartFile empty = new MockMultipartFile("photoFile", new byte[0]);

        assertThatCode(() -> service.validatePhoto(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.validatePhoto(empty)).doesNotThrowAnyException();
        assertThat(service.storePhoto(null)).isNull();
        assertThat(service.storePhoto(empty)).isNull();
        verifyNoInteractions(uploader);
    }

    @Test
    void returnsTheStoredUrl() throws IOException {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://example.com/p.jpg"));

        assertThat(service.storePhoto(Photos.of("image/jpeg", MB))).isEqualTo("https://example.com/p.jpg");
    }

    @Test
    void deletesAStoredPhoto() throws IOException {
        service.deletePhoto("https://res.cloudinary.com/demo/image/upload/v17/pa-ayo/abc123.jpg");

        verify(uploader).destroy("pa-ayo/abc123", Map.of("invalidate", true));
    }

    @Test
    void hasNothingToDeleteWithoutAStoredPhoto() {
        service.deletePhoto(null);
        service.deletePhoto("/images/samples/roadworks.png");

        verifyNoInteractions(uploader);
    }

    @Test
    void survivesAPhotoThatCannotBeDeleted() throws IOException {
        when(uploader.destroy(any(), anyMap())).thenThrow(new IOException("offline"));

        assertThatCode(() -> service.deletePhoto("https://example.com/pa-ayo/abc123.jpg")).doesNotThrowAnyException();
    }

    @Test
    void reportsAStorageFailure() throws IOException {
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException("offline"));

        assertThatThrownBy(() -> service.storePhoto(Photos.of("image/jpeg", MB)))
                .isInstanceOf(PersistenceException.class);
    }
}
