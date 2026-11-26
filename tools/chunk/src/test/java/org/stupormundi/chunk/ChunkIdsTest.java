package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Characterization tests for {@link ChunkIds}. */
class ChunkIdsTest {

    @Test
    void formatsWithSixDigits() {
        assertEquals("c000001", ChunkIds.format(1));
        assertEquals("c000121", ChunkIds.format(121));
    }

    @Test
    void doesNotTruncateNumbersWiderThanSixDigits() {
        assertEquals("c1234567", ChunkIds.format(1234567));
    }

    @Test
    void parsesTheNumberOfAnId() {
        assertEquals(121, ChunkIds.parseNumeric("c000121"));
        assertEquals(7, ChunkIds.parseNumeric("c7"));
    }

    @Test
    void rejectsIdsThatDoNotStartWithC() {
        assertThrows(IllegalArgumentException.class, () -> ChunkIds.parseNumeric("000121"));
        assertThrows(IllegalArgumentException.class, () -> ChunkIds.parseNumeric("x000121"));
    }

    @Test
    void rejectsNullAndTooShortIds() {
        assertThrows(IllegalArgumentException.class, () -> ChunkIds.parseNumeric(null));
        assertThrows(IllegalArgumentException.class, () -> ChunkIds.parseNumeric("c"));
    }

    @Test
    void rejectsANonNumericPart() {
        assertThrows(IllegalArgumentException.class, () -> ChunkIds.parseNumeric("cabc"));
    }
}
