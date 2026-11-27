package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Characterization tests for the word-level fuzzy comparison. */
class FuzzyMatcherIsMatchTest {

    @Test
    void equalWordsMatch() {
        assertTrue(FuzzyMatcher.isMatch("отец", "отец"));
    }

    @Test
    void oneLetterDifferenceMatches() {
        assertTrue(FuzzyMatcher.isMatch("отца", "отец"));
    }

    @Test
    void mangledProperNounWithinDistanceTwoMatches() {
        // WhisperX hears "Гринев" as "горенев".
        assertTrue(FuzzyMatcher.isMatch("горенев", "гринев"));
    }

    @Test
    void distanceThreeDoesNotMatch() {
        assertFalse(FuzzyMatcher.isMatch("привет", "прощай"));
    }

    @Test
    void lengthDifferenceOverThreeNeverMatches() {
        assertFalse(FuzzyMatcher.isMatch("да", "привет"));
    }

    @Test
    void veryShortWordsMatchEachOtherEasily() {
        // Known quirk: with a maximum distance of 2, any two words of up to two letters match.
        assertTrue(FuzzyMatcher.isMatch("он", "на"));
        assertTrue(FuzzyMatcher.isMatch("я", "ты"));
    }
}
