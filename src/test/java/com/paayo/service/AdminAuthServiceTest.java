/*
 * File:        AdminAuthServiceTest.java
 * Module:      Testing
 * Purpose:     Checks admin login and password recovery rules (TC-06, TC-17).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.paayo.model.Admin;
import com.paayo.repository.AdminRepository;
import com.paayo.util.FirestoreTypes;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Tests for AdminAuthService. */
class AdminAuthServiceTest {
    private static final String LINK = "https://pa-ayo.test/admin/reset-password?token=";

    private final AdminRepository repository = mock(AdminRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AdminAuthService service = new AdminAuthService(repository, encoder, mailSender,
            "https://pa-ayo.test", "noreply@pa-ayo.test");

    private Admin admin() {
        Admin admin = new Admin();
        admin.setAdminId("a1");
        admin.setUsername("banilad.admin");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(encoder.encode("correct-password"));
        return admin;
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        return mail.getValue();
    }

    @Test
    void acceptsOnlyTheRightPassword() {
        when(repository.findByUsername("banilad.admin")).thenReturn(admin());

        assertThat(service.authenticate(" Banilad.Admin ", "correct-password")).isTrue();
        assertThat(service.authenticate("banilad.admin", "wrong-password")).isFalse();
        assertThat(service.authenticate("unknown", "correct-password")).isFalse();
        assertThat(service.authenticate(null, "correct-password")).isFalse();
    }

    /** TC-17a: the token is stored hashed for 30 minutes and only the email holds the raw value. */
    @Test
    void sendsAResetLinkForAUsername() {
        when(repository.findByUsername("banilad.admin")).thenReturn(admin());

        service.initiatePasswordReset("banilad.admin");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).saveResetToken(eq("a1"), hash.capture(), expiry.capture());
        assertThat(hash.getValue()).matches("[0-9a-f]{64}");
        assertThat(expiry.getValue()).isBetween(FirestoreTypes.now().plusMinutes(29), FirestoreTypes.now().plusMinutes(30));
        SimpleMailMessage mail = sentMail();
        assertThat(mail.getTo()).containsExactly("admin@example.com");
        assertThat(mail.getFrom()).isEqualTo("noreply@pa-ayo.test");
        assertThat(mail.getText()).containsPattern(java.util.regex.Pattern.quote(LINK) + "[A-Za-z0-9_-]{43}\\s")
                .doesNotContain(hash.getValue());
    }

    @Test
    void sendsAResetLinkForAnEmail() {
        when(repository.findByEmail("admin@example.com")).thenReturn(admin());

        service.initiatePasswordReset("Admin@Example.com");

        assertThat(sentMail().getText()).contains(LINK);
    }

    /** TC-17a: an unknown account causes no write and no email. */
    @Test
    void doesNothingForAnUnknownAccount() {
        service.initiatePasswordReset("stranger");

        verify(repository, never()).saveResetToken(any(), any(), any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void survivesAMailFailure() {
        when(repository.findByUsername("banilad.admin")).thenReturn(admin());
        doThrow(new MailSendException("offline")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.initiatePasswordReset("banilad.admin")).doesNotThrowAnyException();
        sentMail();
    }

    /** TC-17b: the emailed token is the one that validates, and the new password is stored as BCrypt. */
    @Test
    void resetsThePasswordWithTheEmailedToken() {
        Admin admin = admin();
        when(repository.findByUsername("banilad.admin")).thenReturn(admin);
        service.initiatePasswordReset("banilad.admin");
        String token = sentMail().getText().split("token=")[1].split("\\s")[0];
        ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
        verify(repository).saveResetToken(any(), tokenHash.capture(), any());
        admin.setResetTokenExpiresAt(FirestoreTypes.now().plusMinutes(30));
        when(repository.findByResetTokenHash(tokenHash.getValue())).thenReturn(admin);
        when(repository.updatePasswordHash(eq("a1"), anyString())).thenReturn(true);

        assertThat(service.validateResetToken(token)).isTrue();
        assertThat(service.resetPassword(token, "brand-new-password")).isTrue();

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(repository).updatePasswordHash(eq("a1"), newHash.capture());
        assertThat(encoder.matches("brand-new-password", newHash.getValue())).isTrue();
    }

    /** TC-17c: expired, spent, blank and unknown tokens are rejected. */
    @Test
    void rejectsUnusableTokens() {
        Admin expired = admin();
        expired.setResetTokenExpiresAt(FirestoreTypes.now().minusSeconds(1));
        when(repository.findByResetTokenHash(anyString())).thenReturn(expired, admin(), null);

        assertThat(service.validateResetToken("expired")).isFalse();
        assertThat(service.validateResetToken("spent")).isFalse();
        assertThat(service.resetPassword("unknown", "brand-new-password")).isFalse();
        assertThat(service.validateResetToken(" ")).isFalse();
        assertThat(service.validateResetToken(null)).isFalse();
        verify(repository, never()).updatePasswordHash(any(), any());
    }

    @Test
    void enforcesThePasswordLength() {
        assertThat(service.resetPassword("token", "1234567")).isFalse();
        assertThat(service.resetPassword("token", null)).isFalse();
        assertThat(AdminAuthService.isAcceptablePassword("12345678")).isTrue();
        assertThat(AdminAuthService.isAcceptablePassword("x".repeat(72))).isTrue();
        assertThat(AdminAuthService.isAcceptablePassword("x".repeat(73))).isFalse();
        verifyNoInteractions(repository);
    }
}
