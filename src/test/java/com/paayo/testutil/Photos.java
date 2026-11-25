/*
 * File:        Photos.java
 * Module:      Testing
 * Purpose:     Builds in-memory photo files of any type and size for the tests.
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.testutil;

import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockMultipartFile;

/** Fake photo uploads that start with the real file signature of their type. */
public final class Photos {

    private Photos() {
    }

    /** Returns a file of the given image type and exact size in bytes. */
    public static MockMultipartFile of(String contentType, int size) {
        String signature = switch (contentType) {
            case "image/jpeg" -> "\u00FF\u00D8\u00FF";
            case "image/png" -> "\u0089PNG\r\n";
            default -> "RIFF0000WEBP";
        };
        byte[] content = new byte[size];
        byte[] head = signature.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(head, 0, content, 0, head.length);
        return new MockMultipartFile("photoFile", "photo", contentType, content);
    }
}
