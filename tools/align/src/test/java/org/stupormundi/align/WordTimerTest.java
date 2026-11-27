package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic text and times only; nothing here is derived from real audio. */
class WordTimerTest {

    private static List<CueWord> heard(Object... textAndStart) {
        List<CueWord> list = new ArrayList<>();
        for (int i = 0; i < textAndStart.length; i += 2) {
            CueWord w = new CueWord();
            w.text = (String) textAndStart[i];
            w.start = ((Number) textAndStart[i + 1]).doubleValue();
            w.end = w.start + 0.4;
            list.add(w);
        }
        return list;
    }

    @Test
    void spansSplitLikeTheRenderer() {
        assertArrayEquals(new String[] {"—", "Да", "нет"}, WordTimer.spans("  — Да   нет \n"));
        assertEquals(0, WordTimer.spans("").length);
        assertEquals(0, WordTimer.spans(null).length);
    }

    @Test
    void identicalWordsGetTheirOwnTimes() {
        double[] t = WordTimer.wordStarts("мама мыла раму", heard("мама", 1.0, "мыла", 2.0, "раму", 3.0), 1.0, 3.4);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, t, 1e-9);
    }

    @Test
    void aStandaloneDashDoesNotShiftTheFollowingWords() {
        // The dash is a span but is never spoken; the words after it must keep their own times.
        double[] t = WordTimer.wordStarts("— Да нет сказал он", heard("да", 5.0, "нет", 6.0, "сказал", 7.0, "он", 8.0), 5.0, 8.4);
        assertEquals(5, t.length);
        assertEquals(5.0, t[1], 1e-9);
        assertEquals(6.0, t[2], 1e-9);
        assertEquals(7.0, t[3], 1e-9);
        assertEquals(8.0, t[4], 1e-9);
        assertTrue(t[0] >= 5.0 && t[0] <= 5.0 + 1e-9, "dash sits at the cue start");
    }

    @Test
    void aDashInTheMiddleTakesTheTimeOfTheNextWord() {
        double[] t = WordTimer.wordStarts("он сказал — да", heard("он", 1.0, "сказал", 2.0, "да", 3.0), 1.0, 3.4);
        assertEquals(3.0, t[2], 1e-9);
        assertEquals(3.0, t[3], 1e-9);
    }

    @Test
    void aWordTheTranscriptDroppedIsInterpolatedBetweenItsNeighbours() {
        double[] t = WordTimer.wordStarts("аааа бббб вввв", heard("аааа", 1.0, "вввв", 3.0), 1.0, 3.4);
        assertEquals(1.0, t[0], 1e-9);
        assertEquals(2.0, t[1], 1e-9);
        assertEquals(3.0, t[2], 1e-9);
    }

    @Test
    void extraTranscriptWordsDoNotShiftTheRest() {
        double[] t = WordTimer.wordStarts("мама мыла раму",
                heard("мама", 1.0, "вдруг", 1.5, "мыла", 2.0, "раму", 3.0), 1.0, 3.4);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, t, 1e-9);
    }

    @Test
    void yoAndSpellingVariantsStillAlign() {
        double[] t = WordTimer.wordStarts("всё Кузьмич пришёл", heard("все", 1.0, "Кузмич", 2.0, "пришел", 3.0), 1.0, 3.4);
        assertArrayEquals(new double[] {1.0, 2.0, 3.0}, t, 1e-9);
    }

    @Test
    void leadingWordsTheTranscriptDroppedSitBetweenTheCueStartAndTheFirstHeardWord() {
        double[] t = WordTimer.wordStarts("лишнее начало мама мыла", heard("мама", 4.0, "мыла", 5.0), 4.0, 5.4);
        assertEquals(4.0, t[0], 1e-9);
        assertEquals(4.0, t[1], 1e-9);
        assertEquals(4.0, t[2], 1e-9);
        assertEquals(5.0, t[3], 1e-9);
    }

    @Test
    void trailingWordsRunUpToTheCueEnd() {
        double[] t = WordTimer.wordStarts("мама мыла раму тихо", heard("мама", 1.0, "мыла", 2.0), 1.0, 4.0);
        assertEquals(1.0, t[0], 1e-9);
        assertEquals(2.0, t[1], 1e-9);
        assertTrue(t[2] > 2.0 && t[2] < 4.0);
        assertTrue(t[3] > t[2] && t[3] <= 4.0);
    }

    @Test
    void timesNeverDecreaseEvenIfTheTranscriptIsOutOfOrder() {
        double[] t = WordTimer.wordStarts("аааа бббб вввв", heard("аааа", 3.0, "бббб", 2.0, "вввв", 4.0), 2.0, 4.4);
        for (int i = 1; i < t.length; i++) {
            assertTrue(t[i] >= t[i - 1]);
        }
    }

    @Test
    void noTranscriptMatchSpreadsTheWordsAcrossTheCue() {
        double[] t = WordTimer.wordStarts("аааа бббб", heard("яяяя", 1.0, "ююююю", 2.0), 1.0, 3.0);
        assertEquals(1.0, t[0], 1e-9);
        assertTrue(t[1] > 1.0 && t[1] < 3.0);
    }

    @Test
    void emptyParagraphGivesNoTimes() {
        assertEquals(0, WordTimer.wordStarts("", heard("да", 1.0), 1.0, 2.0).length);
    }

    @Test
    void shortWordsOnlyAlignWhenEqual() {
        // "и" must not be paired with a different short word.
        int[] a = WordTimer.align(new String[] {"и", "он"}, new String[] {"в", "ты"});
        assertArrayEquals(new int[] {-1, -1}, a);
    }

    @Test
    void alignPrefersExactOverFuzzyPairs() {
        int[] a = WordTimer.align(new String[] {"савельич"}, new String[] {"савельича", "савельич"});
        assertArrayEquals(new int[] {1}, a);
    }

    @Test
    void refusesToBuildAnAlignmentTableThatWouldNotFitInMemory() {
        org.junit.jupiter.api.Assertions.assertThrows(AlignException.class,
                () -> WordTimer.align(new String[10_000], new String[10_000]));
    }
}
