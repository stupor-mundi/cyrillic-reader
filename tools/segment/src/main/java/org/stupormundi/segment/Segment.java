package org.stupormundi.segment;

/** One chapter-sized audio segment: an entry of {@code audio_index.json}. */
public class Segment {
    public String id;
    public String title;
    public String audioFile;
    public String startParagraph;
    public String contentStartParagraph;
    public String endParagraph;
}
