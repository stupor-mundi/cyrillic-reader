package org.stupormundi.align;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Command line entry point: {@code java -jar align.jar <dataDir> <bookId> <segmentId>}. */
public class Main {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar align.jar <dataDir> <bookId> <segmentId>");
            System.exit(1);
        }

        try {
            run(args[0], args[1], args[2]);
        } catch (AlignException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Reads words/{segmentId}.json, ru.json and audio_index.json from {@code <dataDir>/<bookId>} and writes
     * cues/{segmentId}.json there.
     *
     * @throws AlignException for problems with the input that the user must fix
     * @throws IOException if a file cannot be read or written
     */
    static void run(String dataDir, String bookId, String segmentId) throws IOException {
        Path baseDir = Paths.get(dataDir, bookId);
        Path wordsPath = baseDir.resolve(Paths.get("words", segmentId + ".json"));
        Path ruPath = baseDir.resolve("ru.json");
        Path audioIndexPath = baseDir.resolve("audio_index.json");
        Path outputPath = baseDir.resolve(Paths.get("cues", segmentId + ".json"));

        try {
            InputLoader.ensureFileExists(wordsPath, "Words file not found: ");
            InputLoader.ensureFileExists(ruPath, "ru.json not found: ");
            InputLoader.ensureFileExists(audioIndexPath, "audio_index.json not found: ");
        } catch (IOException e) {
            throw new AlignException(e.getMessage());
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);

        List<Word> words = InputLoader.loadWords(mapper, wordsPath);
        ParagraphIndex paragraphs = InputLoader.loadParagraphs(mapper, ruPath);
        Segment segment = InputLoader.loadSegment(mapper, audioIndexPath, segmentId);

        Aligner.Result aligned = Aligner.align(words, paragraphs, segment);

        CuesResult result = new CuesResult();
        result.bookId = bookId;
        result.audioFile = segment.audioFile;
        result.cues = aligned.cues();

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), result);

        printSummary(aligned);
    }

    private static void printSummary(Aligner.Result aligned) {
        List<Cue> cues = aligned.cues();
        System.err.println("total paragraphs in slice: " + aligned.totalParagraphs());
        System.err.println("paragraphs matched: " + cues.size());
        if (!cues.isEmpty()) {
            System.err.println("first matched paragraphId: " + cues.get(0).paragraphId);
            System.err.println("last matched paragraphId: " + cues.get(cues.size() - 1).paragraphId);
        } else {
            System.err.println("first matched paragraphId: none");
            System.err.println("last matched paragraphId: none");
        }
    }
}
