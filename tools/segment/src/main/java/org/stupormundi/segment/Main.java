package org.stupormundi.segment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Usage: java -jar segment.jar <bookId> <ru.json> <audioDir> <outAudioIndex.json>");
            System.exit(1);
        }

        String bookId = args[0];
        Path ruJsonPath = Paths.get(args[1]);
        Path audioDir = Paths.get(args[2]);
        Path outPath = Paths.get(args[3]);

        try {
            run(bookId, ruJsonPath, audioDir, outPath);
        } catch (NoSuchFileException e) {
            System.err.println("File not found: " + e.getFile());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String bookId, Path ruJsonPath, Path audioDir, Path outPath) throws IOException {
        if (!Files.isRegularFile(ruJsonPath) || !Files.isReadable(ruJsonPath)) {
            throw new IllegalStateException("Cannot read ru.json at '" + ruJsonPath + "'");
        }

        if (!Files.isDirectory(audioDir)) {
            throw new IllegalStateException("Audio directory does not exist or is not a directory: '" + audioDir + "'");
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        BookData book;
        try {
            book = mapper.readValue(ruJsonPath.toFile(), BookData.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ru.json '" + ruJsonPath + "': " + e.getMessage(), e);
        }

        if (book.paragraphs == null || book.paragraphs.isEmpty()) {
            throw new IllegalStateException("ru.json contains no paragraphs");
        }

        // Validate that every paragraph has an id
        for (int i = 0; i < book.paragraphs.size(); i++) {
            Paragraph p = book.paragraphs.get(i);
            if (p == null || p.id == null || p.id.isBlank()) {
                throw new IllegalStateException("Paragraph at index " + i + " is missing an id");
            }
        }

        List<Segment> segments = buildSegments(book.paragraphs);

        List<Path> audioFiles = listAudioFiles(audioDir);

        if (segments.size() != audioFiles.size()) {
            System.err.println("Warning: number of chapters (" + segments.size() + ") "
                    + "differs from number of audio files (" + audioFiles.size() + ")");
        }

        int count = Math.min(segments.size(), audioFiles.size());
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            segment.id = String.format("s%03d", i + 1);
            if (i < count) {
                String fileName = audioFiles.get(i).getFileName().toString();
                segment.audioFile = "audio/" + fileName;
            } else {
                segment.audioFile = null;
            }
        }

        AudioIndex index = new AudioIndex();
        index.bookId = bookId;
        index.segments = segments;

        Path parent = outPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), index);
    }

    private static List<Segment> buildSegments(List<Paragraph> paragraphs) {
        List<Integer> chapterStartIndices = new ArrayList<>();

        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph p = paragraphs.get(i);
            String cyr = p.cyr;
            if (cyr != null && cyr.stripLeading().startsWith("Глава")) {
                chapterStartIndices.add(i);
            }
        }

        if (chapterStartIndices.isEmpty()) {
            throw new IllegalStateException("No chapter markers found (paragraphs starting with 'Глава')");
        }

        List<Segment> segments = new ArrayList<>();

        for (int idx = 0; idx < chapterStartIndices.size(); idx++) {
            int startIndex = chapterStartIndices.get(idx);
            int endIndex;
            if (idx + 1 < chapterStartIndices.size()) {
                endIndex = chapterStartIndices.get(idx + 1) - 1;
            } else {
                endIndex = paragraphs.size() - 1;
            }

            if (endIndex < startIndex) {
                throw new IllegalStateException("Invalid chapter range: end index before start index for chapter at paragraph index " + startIndex);
            }

            Paragraph startParagraph = paragraphs.get(startIndex);
            Paragraph endParagraph = paragraphs.get(endIndex);

            Segment segment = new Segment();
            segment.title = Objects.requireNonNullElse(startParagraph.cyr, "");
            segment.startParagraph = startParagraph.id;
            segment.endParagraph = endParagraph.id;

            segments.add(segment);
        }

        return segments;
    }

    private static List<Path> listAudioFiles(Path audioDir) throws IOException {
        try (Stream<Path> stream = Files.list(audioDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    // Simple data structures for JSON binding
    public static class BookData {
        public String lang;
        public List<Paragraph> paragraphs;
    }

    public static class Paragraph {
        public String id;
        public String cyr;
        public String lat;
    }

    public static class Segment {
        public String id;
        public String title;
        public String audioFile;
        public String startParagraph;
        public String endParagraph;
    }

    public static class AudioIndex {
        public String bookId;
        public List<Segment> segments;
    }
}
