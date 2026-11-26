package org.stupormundi.segment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads {@code ru.json} and the audio directory and writes {@code audio_index.json}. */
final class AudioIndexGenerator {

    private AudioIndexGenerator() {
    }

    static void generate(String bookId, Path ruJsonPath, Path audioDir, Path outPath) throws IOException {
        if (!Files.isRegularFile(ruJsonPath) || !Files.isReadable(ruJsonPath)) {
            throw new IllegalStateException("Cannot read ru.json at '" + ruJsonPath + "'");
        }
        if (!Files.isDirectory(audioDir)) {
            throw new IllegalStateException("Audio directory does not exist or is not a directory: '" + audioDir + "'");
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        List<Paragraph> paragraphs = readParagraphs(mapper, ruJsonPath);
        List<Segment> segments = SegmentBuilder.build(paragraphs);
        List<Path> audioFiles = AudioFiles.list(audioDir);

        if (segments.size() != audioFiles.size()) {
            System.err.println("Warning: number of chapters (" + segments.size() + ") "
                    + "differs from number of audio files (" + audioFiles.size() + ")");
        }
        assignIdsAndAudioFiles(segments, audioFiles);

        AudioIndex index = new AudioIndex();
        index.bookId = bookId;
        index.segments = segments;
        write(mapper, index, outPath);
    }

    private static List<Paragraph> readParagraphs(ObjectMapper mapper, Path ruJsonPath) {
        RuBook book;
        try {
            book = mapper.readValue(ruJsonPath.toFile(), RuBook.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ru.json '" + ruJsonPath + "': " + e.getMessage(), e);
        }

        if (book.paragraphs == null || book.paragraphs.isEmpty()) {
            throw new IllegalStateException("ru.json contains no paragraphs");
        }
        for (int i = 0; i < book.paragraphs.size(); i++) {
            Paragraph p = book.paragraphs.get(i);
            if (p == null || p.id == null || p.id.isBlank()) {
                throw new IllegalStateException("Paragraph at index " + i + " is missing an id");
            }
        }
        return book.paragraphs;
    }

    /** Numbers the segments s001, s002, ... and pairs the Nth segment with the Nth audio file. */
    private static void assignIdsAndAudioFiles(List<Segment> segments, List<Path> audioFiles) {
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            segment.id = String.format("s%03d", i + 1);
            segment.audioFile = i < audioFiles.size()
                    ? "audio/" + audioFiles.get(i).getFileName()
                    : null;
        }
    }

    private static void write(ObjectMapper mapper, AudioIndex index, Path outPath) throws IOException {
        Path parent = outPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), index);
    }
}
