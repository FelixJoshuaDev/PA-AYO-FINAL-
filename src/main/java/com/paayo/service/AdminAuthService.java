/*
 * File:        AdminAuthService.java
 * Module:      Admin security
 * Purpose:     Verifies admin logins and runs the Forgot Password flow (FR-05, FR-13, NFR-06).
 * Responsible: Felix Joshua H. Abing
 * Created:     2026-11-24
 */
package com.paayo.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Administrator authentication rules. */
@Service
public class AdminAuthService {

    /** Returns a username or email trimmed and in lowercase, the form stored in Firestore. */
    public static String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns true for 8 characters or more, up to the 72 bytes BCrypt can hash. */
    public static boolean isAcceptablePassword(String password) {
        return password != null && password.length() >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
