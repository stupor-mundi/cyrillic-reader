package org.stupormundi.segment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link SegmentBuilder}. */
class SegmentBuilderTest {

    private static List<Paragraph> paragraphs(String... texts) {
        List<Paragraph> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            Paragraph p = new Paragraph();
            p.id = String.format("ru-%06d", i + 1);
            p.cyr = texts[i];
            list.add(p);
        }
        return list;
    }

    @Test
    void buildsOneSegmentPerChapterHeading() {
        List<Segment> segments = SegmentBuilder.build(paragraphs(
                "Глава I", "text a", "text b",
                "Глава II", "text c"));

        assertEquals(2, segments.size());

        Segment first = segments.get(0);
        assertEquals("Глава I", first.title);
        assertEquals("ru-000001", first.startParagraph);
        assertEquals("ru-000002", first.contentStartParagraph);
        assertEquals("ru-000003", first.endParagraph);

        Segment second = segments.get(1);
        assertEquals("Глава II", second.title);
        assertEquals("ru-000004", second.startParagraph);
        assertEquals("ru-000005", second.contentStartParagraph);
        assertEquals("ru-000005", second.endParagraph);
    }

    @Test
    void lastChapterRunsToTheEndOfTheBook() {
        List<Segment> segments = SegmentBuilder.build(paragraphs("Глава I", "a", "b", "c"));
        assertEquals("ru-000004", segments.get(0).endParagraph);
    }

    @Test
    void paragraphsBeforeTheFirstChapterBelongToNoSegment() {
        List<Segment> segments = SegmentBuilder.build(paragraphs("Предисловие", "Глава I", "a"));
        assertEquals(1, segments.size());
        assertEquals("ru-000002", segments.get(0).startParagraph);
    }

    @Test
    void headingDetectionIgnoresCaseAndLeadingWhitespace() {
        List<Segment> segments = SegmentBuilder.build(paragraphs("  ГЛАВА I", "a", "\tглава II", "b"));
        assertEquals(2, segments.size());
    }

    @Test
    void anyParagraphStartingWithGlavaCountsAsAHeading() {
        // Known quirk: the check is a plain prefix test, so "Главарь ..." (gang leader) matches too.
        List<Segment> segments = SegmentBuilder.build(paragraphs("Глава I", "a", "Главарь шайки молчал.", "b"));
        assertEquals(2, segments.size());
    }

    @Test
    void headingFollowedByAnotherHeadingUsesItselfAsContentStart() {
        List<Segment> segments = SegmentBuilder.build(paragraphs("Глава I", "Глава II", "a"));
        assertEquals(2, segments.size());
        assertEquals("ru-000001", segments.get(0).startParagraph);
        assertEquals("ru-000001", segments.get(0).contentStartParagraph);
        assertEquals("ru-000001", segments.get(0).endParagraph);
    }

    @Test
    void headingAsTheFinalParagraphUsesItselfAsContentStart() {
        List<Segment> segments = SegmentBuilder.build(paragraphs("Глава I", "a", "Глава II"));
        Segment last = segments.get(1);
        assertEquals("ru-000003", last.startParagraph);
        assertEquals("ru-000003", last.contentStartParagraph);
        assertEquals("ru-000003", last.endParagraph);
    }

    @Test
    void idAndAudioFileAreNotSetHere() {
        Segment segment = SegmentBuilder.build(paragraphs("Глава I", "a")).get(0);
        assertNull(segment.id);
        assertNull(segment.audioFile);
    }

    @Test
    void throwsWhenThereIsNoChapterHeading() {
        IllegalStateException e = assertThrows(
                IllegalStateException.class, () -> SegmentBuilder.build(paragraphs("a", "b")));
        assertEquals("No chapter markers found (paragraphs starting with 'Глава')", e.getMessage());
    }

    @Test
    void nullTextIsTreatedAsNotAHeading() {
        List<Paragraph> list = paragraphs("Глава I", "a");
        list.get(1).cyr = null;
        assertEquals(1, SegmentBuilder.build(list).size());
    }
}
