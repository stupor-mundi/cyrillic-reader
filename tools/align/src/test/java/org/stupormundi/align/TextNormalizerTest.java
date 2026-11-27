package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Characterization tests: they lock the current behaviour of the aligner's text normalisation.
 * They do not claim the behaviour is ideal.
 */
class TextNormalizerTest {

    @Test
    void lowercasesAndDropsPunctuation() {
        assertEquals("привет мир", TextNormalizer.normalizeText("Привет, Мир!"));
    }

    @Test
    void keepsLetterYo() {
        assertEquals("ещё раз", TextNormalizer.normalizeText("Ещё раз."));
    }

    @Test
    void dropsDigits() {
        assertEquals("в году", TextNormalizer.normalizeText("в 1773 году"));
    }

    @Test
    void collapsesWhitespaceAndTrims() {
        assertEquals("а б в", TextNormalizer.normalizeText("  а \t б\n\nв  "));
    }

    @Test
    void hyphenIsDroppedWithoutSplittingTheWord() {
        // "кто-нибудь" becomes one token "ктонибудь", not two.
        assertEquals("ктонибудь", TextNormalizer.normalizeText("кто-нибудь"));
    }

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", TextNormalizer.normalizeText(null));
    }

    @Test
    void standaloneDashProducesNoToken() {
        // The web renderer makes a span for a standalone dash; the aligner has no token for it.
        assertArrayEquals(new String[] {"я", "ехал"}, TextNormalizer.normalizeAndTokenize("— Я — ехал"));
        assertEquals(0, TextNormalizer.normalizeAndTokenize("—").length);
    }

    @Test
    void emptyAndPunctuationOnlyTextGiveNoTokens() {
        assertEquals(0, TextNormalizer.normalizeAndTokenize("").length);
        assertEquals(0, TextNormalizer.normalizeAndTokenize("...!?").length);
    }

    @Test
    void tokenizesOnWhitespace() {
        assertArrayEquals(
                new String[] {"отец", "мой", "андрей", "петрович"},
                TextNormalizer.normalizeAndTokenize("Отец мой Андрей Петрович"));
    }
}
