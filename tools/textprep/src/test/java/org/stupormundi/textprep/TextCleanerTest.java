package org.stupormundi.textprep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Characterization tests for {@link TextCleaner}. */
class TextCleanerTest {

    @Test
    void removesNumericFootnoteMarkers() {
        assertEquals("Текст дальше", TextCleaner.clean("Текст[12] дальше"));
    }

    @Test
    void keepsBracketsThatAreNotNumericFootnotes() {
        assertEquals("a [note] b [1a]", TextCleaner.clean("a [note] b [1a]"));
    }

    @Test
    void removesZeroWidthCharactersAndByteOrderMark() {
        assertEquals("слово", TextCleaner.clean("\uFEFFсло\u200Bво"));
    }

    @Test
    void turnsUnicodeSpacesIntoPlainSpaces() {
        assertEquals("один два три", TextCleaner.clean("один\u00A0два\u2003три"));
    }

    @Test
    void normalizesLineEndings() {
        assertEquals("a\nb\nc", TextCleaner.clean("a\r\nb\rc"));
    }
}
