package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.stupormundi.chunk.TestData.chunk;
import static org.stupormundi.chunk.TestData.enId;
import static org.stupormundi.chunk.TestData.enIndex;
import static org.stupormundi.chunk.TestData.enParagraphs;
import static org.stupormundi.chunk.TestData.oneToOneChunks;
import static org.stupormundi.chunk.TestData.response;
import static org.stupormundi.chunk.TestData.ruId;
import static org.stupormundi.chunk.TestData.ruParagraphs;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link WindowValidator}. */
class WindowValidatorTest {

    /** Three Russian candidates: this is always the "last window", so the 55-65 range does not apply. */
    private static final List<RuParagraph> THREE_RU = ruParagraphs(1, 3);

    private static final List<EnParagraph> THREE_EN = enParagraphs(1, 3);

    private static void validate(ChunkWindowResponse response, String startingChunkId,
                                 List<RuParagraph> ru, List<EnParagraph> en) {
        WindowValidator.validate(response, startingChunkId, ru, en, enIndex(en));
    }

    private static void assertInvalid(String expectedMessagePart, ChunkWindowResponse response,
                                      List<RuParagraph> ru, List<EnParagraph> en) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> validate(response, "c000001", ru, en));
        assertTrue(e.getMessage().contains(expectedMessagePart),
                "expected message containing <" + expectedMessagePart + "> but was <" + e.getMessage() + ">");
    }

    private static ChunkWindowResponse validThree() {
        return response(ruId(3), enId(3),
                chunk("c000001", List.of(ruId(1), ruId(2)), List.of(enId(1), enId(2))),
                chunk("c000002", List.of(ruId(3)), List.of(enId(3))));
    }

    // --- accepted responses

    @Test
    void acceptsAValidResponse() {
        assertDoesNotThrow(() -> validate(validThree(), "c000001", THREE_RU, THREE_EN));
    }

    @Test
    void acceptsChunksWithoutEnglishAndAnEmptyLastEnUsed() {
        ChunkWindowResponse r = response(ruId(3), "",
                chunk("c000001", List.of(ruId(1), ruId(2), ruId(3)), null));
        assertDoesNotThrow(() -> validate(r, "c000001", THREE_RU, THREE_EN));
    }

    @Test
    void acceptsANullLastEnUsedWhenNoEnglishIsUsed() {
        ChunkWindowResponse r = response(ruId(3), null,
                chunk("c000001", List.of(ruId(1), ruId(2), ruId(3)), List.of()));
        assertDoesNotThrow(() -> validate(r, "c000001", THREE_RU, THREE_EN));
    }

    @Test
    void acceptsTheSameEnglishParagraphInConsecutiveChunks() {
        ChunkWindowResponse r = response(ruId(2), enId(1),
                chunk("c000001", List.of(ruId(1)), List.of(enId(1))),
                chunk("c000002", List.of(ruId(2)), List.of(enId(1))));
        assertDoesNotThrow(() -> validate(r, "c000001", ruParagraphs(1, 2), THREE_EN));
    }

    @Test
    void acceptsAStartingChunkIdOtherThanOne() {
        ChunkWindowResponse r = response(ruId(1), enId(1),
                chunk("c000121", List.of(ruId(1)), List.of(enId(1))));
        assertDoesNotThrow(() -> validate(r, "c000121", ruParagraphs(1, 1), THREE_EN));
    }

    @Test
    void acceptsAnyWindowCoveringBetween55And65Paragraphs() {
        List<RuParagraph> seventy = ruParagraphs(1, 70);
        for (int covered : new int[] {55, 60, 65}) {
            ChunkWindowResponse r = response(ruId(covered), "", oneToOneChunks(1, covered, 1, false));
            assertDoesNotThrow(() -> validate(r, "c000001", seventy, List.of()), "covered " + covered);
        }
    }

    // --- rejected: overall shape

    @Test
    void rejectsANullResponse() {
        assertInvalid("Response is null", null, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsAResponseWithoutChunks() {
        assertInvalid("no chunks", response(ruId(3), ""), THREE_RU, THREE_EN);
    }

    @Test
    void rejectsAMissingLastRuCovered() {
        ChunkWindowResponse r = validThree();
        r.lastRuCovered = " ";
        assertInvalid("missing lastRuCovered", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsALastRuCoveredThatIsNotACandidate() {
        ChunkWindowResponse r = validThree();
        r.lastRuCovered = "ru-000099";
        assertInvalid("is not in candidates", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsACandidateWithoutAnId() {
        List<RuParagraph> ru = ruParagraphs(1, 3);
        ru.get(1).id = null;
        assertInvalid("RU candidate paragraph has empty id", validThree(), ru, THREE_EN);
    }

    // --- rejected: how many Russian paragraphs were covered

    @Test
    void rejectsFewerThan55CoveredParagraphsInAWindowThatIsNotTheLast() {
        List<RuParagraph> seventy = ruParagraphs(1, 70);
        ChunkWindowResponse r = response(ruId(54), "", oneToOneChunks(1, 54, 1, false));
        assertInvalid("outside allowed range [55, 65]", r, seventy, List.of());
    }

    @Test
    void rejectsMoreThan65CoveredParagraphs() {
        List<RuParagraph> seventy = ruParagraphs(1, 70);
        ChunkWindowResponse r = response(ruId(66), "", oneToOneChunks(1, 66, 1, false));
        assertInvalid("outside allowed range [55, 65]", r, seventy, List.of());
    }

    // --- rejected: chunk ids

    @Test
    void rejectsAFirstChunkIdThatIsNotTheExpectedOne() {
        ChunkWindowResponse r = response(ruId(1), "",
                chunk("c000007", List.of(ruId(1)), List.of()));
        assertInvalid("expected c000001 but got c000007", r, ruParagraphs(1, 1), THREE_EN);
    }

    @Test
    void rejectsAGapInTheChunkIds() {
        ChunkWindowResponse r = response(ruId(2), "",
                chunk("c000001", List.of(ruId(1)), List.of()),
                chunk("c000003", List.of(ruId(2)), List.of()));
        assertInvalid("mismatch at index 1: expected c000002 but got c000003", r, ruParagraphs(1, 2), THREE_EN);
    }

    @Test
    void rejectsAChunkWithoutAnId() {
        ChunkWindowResponse r = response(ruId(1), "", chunk("", List.of(ruId(1)), List.of()));
        assertInvalid("has empty id", r, ruParagraphs(1, 1), THREE_EN);
    }

    @Test
    void rejectsAChunkIdInTheWrongFormat() {
        ChunkWindowResponse r = response(ruId(1), "", chunk("x000001", List.of(ruId(1)), List.of()));
        assertInvalid("Invalid chunk id format", r, ruParagraphs(1, 1), THREE_EN);
    }

    // --- rejected: Russian coverage

    @Test
    void rejectsAChunkWithoutRussianParagraphs() {
        ChunkWindowResponse r = response(ruId(1), "",
                chunk("c000001", List.of(), List.of()));
        assertInvalid("has empty 'ru' list", r, ruParagraphs(1, 1), THREE_EN);
    }

    @Test
    void rejectsAParagraphThatIsNotCovered() {
        ChunkWindowResponse r = response(ruId(3), "",
                chunk("c000001", List.of(ruId(1)), List.of()),
                chunk("c000002", List.of(ruId(3)), List.of()));
        assertInvalid("RU paragraph ru-000002 not covered", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsAParagraphThatAppearsInTwoChunks() {
        ChunkWindowResponse r = response(ruId(3), "",
                chunk("c000001", List.of(ruId(1), ruId(2)), List.of()),
                chunk("c000002", List.of(ruId(2), ruId(3)), List.of()));
        assertInvalid("ru-000002 appears in multiple chunks", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsAParagraphOutsideTheCandidates() {
        ChunkWindowResponse r = response(ruId(3), "",
                chunk("c000001", List.of(ruId(1), ruId(2), ruId(3), "ru-000099"), List.of()));
        assertInvalid("outside candidates: ru-000099", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsAParagraphAfterLastRuCovered() {
        List<RuParagraph> sixty = ruParagraphs(1, 60);
        Chunk[] chunks = oneToOneChunks(1, 58, 1, false);
        chunks[57] = chunk("c000058", List.of(ruId(58), ruId(59)), List.of());
        assertInvalid("ru-000059 which is after lastRuCovered", response(ruId(58), "", chunks), sixty, List.of());
    }

    // --- rejected: English

    @Test
    void rejectsAnEnglishParagraphOutsideTheWindow() {
        ChunkWindowResponse r = response(ruId(1), enId(4),
                chunk("c000001", List.of(ruId(1)), List.of(enId(4))));
        assertInvalid("outside window: en-000004", r, ruParagraphs(1, 1), THREE_EN);
    }

    @Test
    void rejectsEnglishParagraphsThatGoBackwards() {
        ChunkWindowResponse r = response(ruId(2), enId(3),
                chunk("c000001", List.of(ruId(1)), List.of(enId(3))),
                chunk("c000002", List.of(ruId(2)), List.of(enId(1))));
        assertInvalid("not monotonic: en-000001", r, ruParagraphs(1, 2), THREE_EN);
    }

    @Test
    void rejectsAWrongLastEnUsed() {
        ChunkWindowResponse r = validThree();
        r.lastEnUsed = enId(2);
        assertInvalid("lastEnUsed mismatch: expected en-000003 but got en-000002", r, THREE_RU, THREE_EN);
    }

    @Test
    void rejectsALastEnUsedWhenNoEnglishWasUsed() {
        ChunkWindowResponse r = response(ruId(1), enId(1),
                chunk("c000001", List.of(ruId(1)), List.of()));
        assertInvalid("No EN ids used but lastEnUsed", r, ruParagraphs(1, 1), THREE_EN);
    }
}
