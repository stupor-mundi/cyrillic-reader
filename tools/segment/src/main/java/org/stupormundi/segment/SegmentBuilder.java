package org.stupormundi.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Finds chapter headings in the Russian paragraphs and turns each chapter into a {@link Segment}. */
final class SegmentBuilder {

    private static final String CHAPTER_PREFIX = "глава";

    private SegmentBuilder() {
    }

    /**
     * Builds one segment per chapter heading. A heading is a paragraph whose text, ignoring leading
     * whitespace and case, starts with "глава". A chapter runs to the paragraph before the next heading,
     * or to the end of the book. Paragraphs before the first heading belong to no segment.
     *
     * <p>The segments have no {@code id} and no {@code audioFile} yet.
     *
     * @throws IllegalStateException if there is no chapter heading
     */
    static List<Segment> build(List<Paragraph> paragraphs) {
        List<Integer> chapterStarts = findChapterStarts(paragraphs);
        if (chapterStarts.isEmpty()) {
            throw new IllegalStateException("No chapter markers found (paragraphs starting with 'Глава')");
        }

        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < chapterStarts.size(); i++) {
            int startIndex = chapterStarts.get(i);
            int endIndex = i + 1 < chapterStarts.size()
                    ? chapterStarts.get(i + 1) - 1
                    : paragraphs.size() - 1;
            segments.add(buildSegment(paragraphs, startIndex, endIndex));
        }
        return segments;
    }

    private static List<Integer> findChapterStarts(List<Paragraph> paragraphs) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String cyr = paragraphs.get(i).cyr;
            String normalized = cyr == null ? "" : cyr.stripLeading().toLowerCase();
            if (normalized.startsWith(CHAPTER_PREFIX)) {
                starts.add(i);
            }
        }
        return starts;
    }

    private static Segment buildSegment(List<Paragraph> paragraphs, int startIndex, int endIndex) {
        if (endIndex < startIndex) {
            throw new IllegalStateException(
                    "Invalid chapter range: end index before start index for chapter at paragraph index " + startIndex);
        }

        Paragraph start = paragraphs.get(startIndex);
        Paragraph end = paragraphs.get(endIndex);

        Segment segment = new Segment();
        segment.title = Objects.requireNonNullElse(start.cyr, "");
        segment.startParagraph = start.id;
        segment.endParagraph = end.id;
        segment.contentStartParagraph = contentStart(paragraphs, startIndex, endIndex);
        return segment;
    }

    /** The first paragraph after the heading, or the heading itself if the chapter has no content. */
    private static String contentStart(List<Paragraph> paragraphs, int startIndex, int endIndex) {
        Paragraph start = paragraphs.get(startIndex);
        int contentIndex = startIndex + 1;
        if (contentIndex <= endIndex) {
            return paragraphs.get(contentIndex).id;
        }

        if (startIndex == paragraphs.size() - 1) {
            System.err.println("Warning: chapter marker at paragraph '" + start.id + "' is the final paragraph of the book");
        } else {
            System.err.println("Warning: chapter marker at paragraph '" + start.id + "' is immediately followed by another chapter marker");
        }
        return start.id;
    }
}
