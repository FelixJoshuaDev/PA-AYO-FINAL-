/*
 * File:        Emulator.java
 * Module:      Testing
 * Purpose:     Shared settings and clean-up for tests that use the Firestore emulator.
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.testutil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Firestore emulator helper; uses its own project so development data is untouched. */
public final class Emulator {
    public static final String HOST = "127.0.0.1:8085";
    public static final String PROJECT_ID = "demo-pa-ayo-test";

    private Emulator() {
    }

    /** Deletes every document in the test project. */
    public static void clear() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + HOST + "/emulator/v1/projects/"
                + PROJECT_ID + "/databases/(default)/documents")).DELETE().build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }
}
