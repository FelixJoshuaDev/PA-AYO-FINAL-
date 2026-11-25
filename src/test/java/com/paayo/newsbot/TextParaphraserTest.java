/*
 * File:        TextParaphraserTest.java
 * Module:      Testing
 * Purpose:     Checks summary length and sentence handling (FR-07, TC-11).
 * Responsible: Reuel Matthew Arcilla
 */
package com.paayo.newsbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tests for TextParaphraser (TC-11). */
class TextParaphraserTest {
    private final TextParaphraser paraphraser = new TextParaphraser(60);

    @Test
    void returnsAnEmptySummaryForBlankInput() {
        assertThat(paraphraser.paraphrase(null)).isEmpty();
        assertThat(paraphraser.paraphrase("   ")).isEmpty();
        assertThat(paraphraser.paraphrase("<p> </p>")).isEmpty();
    }

    @Test
    void keepsShortTextAndRemovesHtmlAndExtraSpaces() {
        assertThat(paraphraser.paraphrase("<p>Road   closed\n today.</p>")).isEqualTo("Road closed today.");
    }

    @Test
    void keepsWholeSentencesUpToTheLimit() {
        assertThat(paraphraser.paraphrase("First sentence is here. Second one fits too! Third one is far too long to fit."))
                .isEqualTo("First sentence is here. Second one fits too!");
    }

    @Test
    void acceptsASentenceOfExactlyTheMaximumLength() {
        String exact = "x".repeat(59) + ".";

        assertThat(paraphraser.paraphrase(exact + " More.")).isEqualTo(exact);
    }

    @Test
    void cutsASingleLongSentenceAtAWord() {
        String summary = paraphraser.paraphrase("word ".repeat(40));

        assertThat(summary).endsWith("word...").hasSizeLessThanOrEqualTo(60);
    }

    @Test
    void cutsASingleLongWord() {
        assertThat(paraphraser.paraphrase("x".repeat(200))).isEqualTo("x".repeat(57) + "...");
    }

    @Test
    void doesNotEndASentenceAtAnAbbreviation() {
        assertThat(new TextParaphraser(30).paraphrase("Roads opened. They thanked Gov. Cruz for the help."))
                .isEqualTo("Roads opened.");
        assertThat(new TextParaphraser(40).paraphrase("Roads opened. As of 3:40 p.m. the flood was still rising."))
                .isEqualTo("Roads opened.");
    }
}
