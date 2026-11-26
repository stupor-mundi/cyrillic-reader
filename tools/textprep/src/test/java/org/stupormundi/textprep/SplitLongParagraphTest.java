package org.stupormundi.textprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link Main#splitLongParagraph(String, int)}. */
class SplitLongParagraphTest {

    private static final int MAX = 1200;

    @Test
    void shortTextIsReturnedUnchanged() {
        assertEquals(List.of("Короткий текст."), Main.splitLongParagraph("Короткий текст.", MAX));
    }

    @Test
    void textOfExactlyMaxLengthIsNotSplit() {
        String text = "a".repeat(MAX);
        assertEquals(List.of(text), Main.splitLongParagraph(text, MAX));
    }

    @Test
    void splitsAtTheSentenceBoundaryClosestToTheMiddle() {
        String first = "x".repeat(799) + ".";
        String second = "y".repeat(799) + ".";
        List<String> parts = Main.splitLongParagraph(first + " " + second, MAX);
        assertEquals(List.of(first, second), parts);
    }

    @Test
    void prefersSentenceBoundaryOverCloserCommaBoundary() {
        String first = "a".repeat(100) + ".";
        String rest = "bb, ".repeat(290).trim();
        List<String> parts = Main.splitLongParagraph(first + " " + rest, MAX);
        assertEquals(List.of(first, rest), parts);
    }

    @Test
    void fallsBackToSpaceClosestToTheMiddleWhenThereIsNoPunctuation() {
        String text = "word ".repeat(300).trim();
        List<String> parts = Main.splitLongParagraph(text, MAX);
        assertEquals(2, parts.size());
        assertEquals(text, String.join(" ", parts));
    }

    @Test
    void splitsAtTheMiddleWhenThereAreNoSpaces() {
        List<String> parts = Main.splitLongParagraph("a".repeat(2500), MAX);
        // 2500 -> 1250 + 1250 -> each 1250 > 1200 is split again into 625 + 625.
        assertEquals(List.of(625, 625, 625, 625), parts.stream().map(String::length).toList());
    }

    @Test
    void everyPartFitsAndNothingIsLostForLongMultiSentenceText() {
        String text = "Это очень длинное предложение. ".repeat(200).trim();
        List<String> parts = Main.splitLongParagraph(text, MAX);
        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(p -> p.length() <= MAX));
        assertTrue(parts.stream().noneMatch(p -> !p.equals(p.trim())));
        assertEquals(text, String.join(" ", parts));
    }
}
