/*
 * File:        AdminSeederTest.java
 * Module:      Testing
 * Purpose:     Checks when the first administrator is created.
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paayo.exception.PersistenceException;
import com.paayo.model.Admin;
import com.paayo.repository.AdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Tests for AdminSeeder. */
class AdminSeederTest {
    private final AdminRepository repository = mock(AdminRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AdminSeeder seeder(String username, String email, String password) {
        return new AdminSeeder(repository, encoder, username, email, password, "127.0.0.1:8085");
    }

    @Test
    void savesAHashedAdminWhenNoneExists() {
        when(repository.isEmpty()).thenReturn(true);

        seeder(" Banilad.Admin ", "Admin@Example.com", "password1").seedFirstAdmin();

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("banilad.admin");
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(encoder.matches("password1", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getResetTokenHash()).isNull();
    }

    @Test
    void doesNothingWhenAnAdminExists() {
        seeder("admin", "admin@example.com", "password1").seedFirstAdmin();

        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"'',admin@example.com,password1", "admin,not-an-email,password1", "admin,admin@example.com,short"})
    void refusesIncompleteSettings(String username, String email, String password) {
        when(repository.isEmpty()).thenReturn(true);

        seeder(username, email, password).seedFirstAdmin();

        verify(repository, never()).save(any());
    }

    @Test
    void refusesPlaceholderValuesOutsideTheEmulator() {
        when(repository.isEmpty()).thenReturn(true);

        new AdminSeeder(repository, encoder, "admin", "Admin@Example.com", "password1", "").seedFirstAdmin();
        verify(repository, never()).save(any());

        new AdminSeeder(repository, encoder, "admin", "admin@mandaue.gov.ph", "password1", "").seedFirstAdmin();
        verify(repository).save(any());
    }

    @Test
    void keepsTheSiteRunningWhenFirestoreIsDown() {
        when(repository.isEmpty()).thenThrow(new PersistenceException("down", null));

        assertThatCode(() -> seeder("admin", "admin@example.com", "password1").seedFirstAdmin())
                .doesNotThrowAnyException();
    }
}
