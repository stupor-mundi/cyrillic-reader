package org.stupormundi.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Command line entry point: aligns {@code ru.json} and {@code en.json} into {@code chunks.json}. */
public class Main {

    private static final String USAGE = """
            Usage: java -jar chunk.jar <dataDir> <bookId>
            """;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Error: expected 2 arguments, got " + args.length);
            System.err.println(USAGE);
            System.exit(1);
        }

        String dataDir = args[0];
        String bookId = args[1];
        Path baseDir = Path.of(dataDir, bookId);
        Path ruPath = baseDir.resolve("ru.json");
        Path enPath = baseDir.resolve("en.json");
        Path outPath = baseDir.resolve("chunks.json");

        if (!Files.exists(ruPath)) {
            throw new IllegalArgumentException("ru.json not found: " + ruPath);
        }
        if (!Files.exists(enPath)) {
            throw new IllegalArgumentException("en.json not found: " + enPath);
        }

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Environment variable ANTHROPIC_API_KEY must be set.");
        }

        RuBook ruBook = Json.MAPPER.readValue(ruPath.toFile(), RuBook.class);
        EnBook enBook = Json.MAPPER.readValue(enPath.toFile(), EnBook.class);

        if (!"ru".equalsIgnoreCase(ruBook.lang)) {
            System.err.println("Warning: ru.json lang is '" + ruBook.lang + "', expected 'ru'. Continuing.");
        }
        if (!"en".equalsIgnoreCase(enBook.lang)) {
            System.err.println("Warning: en.json lang is '" + enBook.lang + "', expected 'en'. Continuing.");
        }

        List<Chunk> chunks = new ChunkAligner(new AnthropicClient(apiKey)).align(ruBook, enBook);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("bookId", bookId);
        output.put("chunks", chunks);

        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }
        ObjectMapper prettyMapper = Json.MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);
        prettyMapper.writeValue(outPath.toFile(), output);
    }
}
