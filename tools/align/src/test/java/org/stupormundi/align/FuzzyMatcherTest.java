package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for {@link FuzzyMatcher#fuzzyMatch}. They use made-up sentences and made-up
 * timings. They lock current behaviour, including quirks, and do not claim it is ideal.
 */
class FuzzyMatcherTest {

    /** A word that is far too long to match any test token (length difference over 3). */
    private static final String NOISE = "щщщщщщщщщщщщщщ";

    private static List<Word> words(String... texts) {
        List<Word> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            Word w = new Word();
            w.word = texts[i];
            w.norm = TextNormalizer.normalizeText(texts[i]);
            w.start = i;
            w.end = i + 0.5;
            list.add(w);
        }
        return list;
    }

    private static String[] tokens(String text) {
        return TextNormalizer.normalizeAndTokenize(text);
    }

    private static final String[] NONE = new String[0];

    @Test
    void exactMatchReturnsFirstAndLastWordIndex() {
        List<Word> w = words("мой", "отец", "служил", "при", "графе");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец служил при графе"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(0, m.firstMatchedWordIndex);
        assertEquals(4, m.lastMatchedWordIndex);
        assertEquals(5, m.matchedCount);
        assertEquals(0, m.wordOffset);
    }

    @Test
    void matchStartsAtFirstMatchingWordAfterSearchPos() {
        List<Word> w = words(NOISE, NOISE, "мой", "отец", "служил");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец служил"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(2, m.firstMatchedWordIndex);
        assertEquals(4, m.lastMatchedWordIndex);
    }

    @Test
    void searchPosSkipsEarlierOccurrences() {
        List<Word> w = words("мой", "отец", "потом", "мой", "отец");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец"), NONE, w, 2);
        assertNotNull(m);
        assertEquals(3, m.firstMatchedWordIndex);
        assertEquals(4, m.lastMatchedWordIndex);
    }

    @Test
    void mangledProperNounStillMatches() {
        List<Word> w = words("и", "горенев", "ответил", "мне");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("Гринев ответил мне"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(1, m.firstMatchedWordIndex);
        assertEquals(3, m.lastMatchedWordIndex);
        assertEquals(3, m.matchedCount);
    }

    @Test
    void returnsNullWhenNothingMatches() {
        List<Word> w = words(NOISE, NOISE, NOISE);
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("мой отец служил"), NONE, w, 0));
    }

    @Test
    void returnsNullForEmptyInputs() {
        List<Word> w = words("мой", "отец");
        assertNull(FuzzyMatcher.fuzzyMatch(NONE, NONE, w, 0));
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("мой отец"), NONE, new ArrayList<>(), 0));
    }

    @Test
    void returnsNullWhenSearchPosIsPastTheEnd() {
        List<Word> w = words("мой", "отец");
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("мой отец"), NONE, w, 2));
    }

    @Test
    void searchWindowLimitsWhereAScanMayStart() {
        // Scans may start up to 300 words after searchPos. For a one-token paragraph a scan is over after
        // three mismatches (every third mismatch advances the token index past the only token), so the
        // matching word can be at most 302 words ahead.
        assertNotNull(FuzzyMatcher.fuzzyMatch(tokens("мой"), NONE, wordsWithMatchAt(302), 0));
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("мой"), NONE, wordsWithMatchAt(303), 0));
    }

    private static List<Word> wordsWithMatchAt(int index) {
        String[] texts = new String[index + 1];
        for (int i = 0; i < index; i++) {
            texts[i] = NOISE;
        }
        texts[index] = "мой";
        return words(texts);
    }

    @Test
    void gapOfTwoUnrelatedWordsInsideTheParagraphResyncs() {
        List<Word> w = words("мой", "отец", NOISE, NOISE, "служил", "при", "графе");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец служил при графе"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(0, m.firstMatchedWordIndex);
        assertEquals(6, m.lastMatchedWordIndex);
        assertEquals(5, m.matchedCount);
    }

    @Test
    void gapOfThreeUnrelatedWordsCostsOneToken() {
        // Every third consecutive mismatch also advances the token index, so "служил" is skipped.
        List<Word> w = words("мой", "отец", NOISE, NOISE, NOISE, "служил", "при", "графе");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец служил при графе"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(0, m.firstMatchedWordIndex);
        assertEquals(7, m.lastMatchedWordIndex);
        assertEquals(4, m.matchedCount);
    }

    @Test
    void matchStopsExtendingAfterTenConsecutiveMismatches() {
        List<Word> w = new ArrayList<>(words("мой"));
        List<Word> noise = words(NOISE, NOISE, NOISE, NOISE, NOISE, NOISE, NOISE, NOISE, NOISE, NOISE);
        w.addAll(noise);
        w.addAll(words("отец", "служил"));
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой отец служил"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(0, m.firstMatchedWordIndex);
        assertEquals(0, m.lastMatchedWordIndex);
        assertEquals(1, m.matchedCount);
    }

    @Test
    void atLeastThirtyPercentOfTokensMustMatch() {
        String[] ten = tokens("первое второе третье четвертое пятое шестое седьмое восьмое девятое десятое");
        // 2 of 10 tokens heard: below the threshold of 3 (int(0.3 * 10)).
        assertNull(FuzzyMatcher.fuzzyMatch(ten, NONE, words("первое", "второе", NOISE, NOISE, NOISE), 0));
        // 3 of 10 tokens heard: accepted.
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(ten, NONE, words("первое", "второе", "третье", NOISE), 0);
        assertNotNull(m);
        assertEquals(3, m.matchedCount);
        assertEquals(2, m.lastMatchedWordIndex);
    }

    @Test
    void shortParagraphNeedsAtLeastOneMatch() {
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("отец"), NONE, words(NOISE, "отец"), 0);
        assertNotNull(m);
        assertEquals(1, m.firstMatchedWordIndex);
    }

    @Test
    void lookaheadTokensDoNotCountAsMatchedAndDoNotMoveTheLastIndex() {
        List<Word> w = words("мой", "отец", "служил");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("мой"), tokens("отец служил"), w, 0);
        assertNotNull(m);
        assertEquals(0, m.firstMatchedWordIndex);
        assertEquals(0, m.lastMatchedWordIndex);
        assertEquals(1, m.matchedCount);
    }

    @Test
    void lookaheadAloneIsNotEnoughForAMatch() {
        List<Word> w = words(NOISE, "отец", "служил");
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("мой"), tokens("отец служил"), w, 0));
    }

    @Test
    void leadingWordsHeardAsUnrelatedWordsGiveAWordOffset() {
        // The paragraph starts with "дорожные" but the transcript has three unrelated words instead.
        // After three mismatches the token index moves on, so the first matched token is 1.
        List<Word> w = words(NOISE, NOISE, NOISE, "размышления", "я", "ехал");
        FuzzyMatcher.MatchResult m = FuzzyMatcher.fuzzyMatch(tokens("дорожные размышления я ехал"), NONE, w, 0);
        assertNotNull(m);
        assertEquals(3, m.firstMatchedWordIndex);
        assertEquals(5, m.lastMatchedWordIndex);
        assertEquals(1, m.wordOffset);
    }

    @Test
    void missingFirstWordWithoutExtraTranscriptWordsGivesNoMatchAtAll() {
        // Quirk: a dropped leading word is NOT recovered unless the transcript has about three words
        // in its place. Here the only transcript words are the matching ones, so each one is consumed
        // as a mismatch against token 0 and the whole paragraph is unmatched.
        List<Word> w = words("размышления", "я", "ехал", "в", "крепость");
        assertNull(FuzzyMatcher.fuzzyMatch(tokens("дорожные размышления я ехал в крепость"), NONE, w, 0));
    }
}
