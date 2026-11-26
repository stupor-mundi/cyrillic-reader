package org.stupormundi.textprep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Command line entry point: turns {@code ru.txt} and {@code en.txt} into {@code ru.json} and {@code en.json}. */
public class Main {

    /** Paragraphs longer than this are split at natural boundaries. */
    private static final int MAX_PARAGRAPH_CHARS = 1200;

    private static final String USAGE = """
        Usage: java -jar textprep.jar <dataDir> <bookId>
        Example: java -jar textprep.jar ../../data pushkin_kd
        """;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println(USAGE);
            System.exit(1);
        }
        String dataDir = args[0];
        String bookId = args[1];

        Path baseDir = Path.of(dataDir, bookId);
        processLanguage(Language.RU, baseDir.resolve("ru.txt"), baseDir.resolve("ru.json"));
        processLanguage(Language.EN, baseDir.resolve("en.txt"), baseDir.resolve("en.json"));
    }

    private static void processLanguage(Language language, Path inputPath, Path outputPath) {
        String content = readInput(inputPath);

        String cleaned = TextCleaner.clean(content);
        List<String> paragraphs = ParagraphSplitter.splitIntoParagraphs(cleaned, MAX_PARAGRAPH_CHARS);
        Map<String, Object> output = ParagraphJson.build(language, paragraphs);

        writeOutput(outputPath, output);
    }

    private static String readInput(Path inputPath) {
        if (!Files.exists(inputPath)) {
            System.err.println("Error: input file not found: " + inputPath.toAbsolutePath());
            System.exit(1);
        }
        try {
            return Files.readString(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error: cannot read input file " + inputPath.toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
            return null; // unreachable
        }
    }

    private static void writeOutput(Path outputPath, Map<String, Object> output) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8), output);
        } catch (IOException e) {
            System.err.println("Error: cannot write output file " + outputPath.toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
