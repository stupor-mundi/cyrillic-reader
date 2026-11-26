package org.stupormundi.textprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link ParagraphSplitter}. */
class ParagraphSplitterTest {

    private static final int MAX = 1200;

    @Test
    void shortTextIsReturnedUnchanged() {
        assertEquals(List.of("Короткий текст."), ParagraphSplitter.splitLongParagraph("Короткий текст.", MAX));
    }

    @Test
    void textOfExactlyMaxLengthIsNotSplit() {
        String text = "a".repeat(MAX);
        assertEquals(List.of(text), ParagraphSplitter.splitLongParagraph(text, MAX));
    }

    @Test
    void splitsAtTheSentenceBoundaryClosestToTheMiddle() {
        String first = "x".repeat(799) + ".";
        String second = "y".repeat(799) + ".";
        List<String> parts = ParagraphSplitter.splitLongParagraph(first + " " + second, MAX);
        assertEquals(List.of(first, second), parts);
    }

    @Test
    void prefersSentenceBoundaryOverCloserCommaBoundary() {
        String first = "a".repeat(100) + ".";
        String rest = "bb, ".repeat(290).trim();
        List<String> parts = ParagraphSplitter.splitLongParagraph(first + " " + rest, MAX);
        assertEquals(List.of(first, rest), parts);
    }

    @Test
    void usesSemicolonOrColonWhenThereIsNoSentenceBoundary() {
        String first = "a".repeat(300) + ";";
        String rest = "bb, ".repeat(300).trim();
        List<String> parts = ParagraphSplitter.splitLongParagraph(first + " " + rest, MAX);
        assertEquals(List.of(first, rest), parts);
    }

    @Test
    void fallsBackToSpaceClosestToTheMiddleWhenThereIsNoPunctuation() {
        String text = "word ".repeat(300).trim();
        List<String> parts = ParagraphSplitter.splitLongParagraph(text, MAX);
        assertEquals(2, parts.size());
        assertEquals(text, String.join(" ", parts));
    }

    @Test
    void splitsAtTheMiddleWhenThereAreNoSpaces() {
        List<String> parts = ParagraphSplitter.splitLongParagraph("a".repeat(2500), MAX);
        // 2500 -> 1250 + 1250 -> each 1250 > 1200 is split again into 625 + 625.
        assertEquals(List.of(625, 625, 625, 625), parts.stream().map(String::length).toList());
    }

    @Test
    void everyPartFitsAndNothingIsLostForLongMultiSentenceText() {
        String text = "Это очень длинное предложение. ".repeat(200).trim();
        List<String> parts = ParagraphSplitter.splitLongParagraph(text, MAX);
        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(p -> p.length() <= MAX));
        assertTrue(parts.stream().noneMatch(p -> !p.equals(p.trim())));
        assertEquals(text, String.join(" ", parts));
    }

    @Test
    void splitIntoParagraphsBreaksOnEmptyLinesAndDropsBlankParagraphs() {
        List<String> paragraphs = ParagraphSplitter.splitIntoParagraphs("one\n\n\n  two  \n  \nthree\n\n", MAX);
        assertEquals(List.of("one", "two", "three"), paragraphs);
    }

    @Test
    void splitIntoParagraphsKeepsSingleNewlinesInsideAParagraph() {
        assertEquals(List.of("line one\nline two"), ParagraphSplitter.splitIntoParagraphs("line one\nline two", MAX));
    }

    @Test
    void splitIntoParagraphsSplitsOverlongParagraphs() {
        String text = "Это очень длинное предложение. ".repeat(100).trim();
        List<String> paragraphs = ParagraphSplitter.splitIntoParagraphs(text, MAX);
        assertTrue(paragraphs.size() > 1);
        assertTrue(paragraphs.stream().allMatch(p -> p.length() <= MAX));
    }
}
