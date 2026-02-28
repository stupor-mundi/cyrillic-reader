package org.stupormundi.translit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class Main {
    private static final String USAGE = """
        Usage: java -jar textprep.jar <mode> <input.txt> <output.json>
        Modes: ru (Russian + transliteration), en (English)
        """;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(USAGE);
            System.exit(1);
        }
        String mode = args[0];
        String inputPath = args[1];
        String outputPath = args[2];

        if (!mode.equals("ru") && !mode.equals("en")) {
            System.err.println("Error: mode must be 'ru' or 'en'");
            System.err.println(USAGE);
            System.exit(1);
        }

        Path in = Path.of(inputPath);
        if (!Files.exists(in)) {
            System.err.println("Error: input file not found: " + inputPath);
            System.exit(1);
        }

        String content ="";
        try {
            content = Files.readString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error: cannot read input file: " + e.getMessage());
            System.exit(1);
        }

        // Normalize line endings
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        // Split on paragraph boundaries (one or more empty lines)
        Pattern paraSplit = Pattern.compile("\\n\\s*\\n+");
        List<String> rawParas = Arrays.stream(paraSplit.split(content))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        String prefix = mode + "-";
        List<Map<String, String>> paragraphs = new ArrayList<>();
        Transliterator transliterator = mode.equals("ru") ? Transliterator.getInstance("Cyrillic-Latin") : null;

        for (int i = 0; i < rawParas.size(); i++) {
            String id = String.format("%s%06d", prefix, i + 1);
            String text = rawParas.get(i);
            if (mode.equals("ru")) {
                String lat = transliterator.transliterate(text);
                paragraphs.add(Map.of("id", id, "cyr", text, "lat", lat));
            } else {
                paragraphs.add(Map.of("id", id, "text", text));
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lang", mode);
        output.put("paragraphs", paragraphs);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            mapper.writeValue(Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8), output);
        } catch (IOException e) {
            System.err.println("Error: cannot write output file: " + e.getMessage());
            System.exit(1);
        }
    }
}
