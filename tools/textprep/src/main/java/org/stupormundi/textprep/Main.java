package org.stupormundi.textprep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.icu.text.Transliterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Main {
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
        Path ruInput = baseDir.resolve("ru.txt");
        Path enInput = baseDir.resolve("en.txt");
        Path ruOutput = baseDir.resolve("ru.json");
        Path enOutput = baseDir.resolve("en.json");

        processLanguage("ru", ruInput, ruOutput);
        processLanguage("en", enInput, enOutput);
    }

    private static void processLanguage(String mode, Path inputPath, Path outputPath) {
        if (!Files.exists(inputPath)) {
            System.err.println("Error: input file not found: " + inputPath.toAbsolutePath());
            System.exit(1);
        }

        String content = "";
        try {
            content = Files.readString(inputPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error: cannot read input file " + inputPath.toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
        }

        // Text cleanup operations applied immediately after reading
        content = content.replaceAll("\\[\\d+\\]", "");
        content = content.replaceAll("[\\p{Cf}\\uFEFF]", "");
        content = content.replaceAll("\\p{Zs}", " ");

        // Normalize line endings
        content = content.replace("\r\n", "\n").replace("\r", "\n");

        // Split on paragraph boundaries (one or more empty lines)
        Pattern paraSplit = Pattern.compile("\\n\\s*\\n+");
        List<String> rawParas = Arrays.stream(paraSplit.split(content))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> limitedParas = new ArrayList<>();
        for (String para : rawParas) {
            limitedParas.addAll(splitLongParagraph(para, 1200));
        }
        rawParas = limitedParas;

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
            mapper.writeValue(Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8), output);
        } catch (IOException e) {
            System.err.println("Error: cannot write output file " + outputPath.toAbsolutePath() + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<String> splitLongParagraph(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }

        int length = text.length();
        int mid = length / 2;
        int splitPoint = -1;

        List<Integer> positions = new ArrayList<>();

        // a) ". ", "? ", "! "
        for (int i = 1; i < length; i++) {
            char prev = text.charAt(i - 1);
            char curr = text.charAt(i);
            if (curr == ' ' && (prev == '.' || prev == '?' || prev == '!')) {
                positions.add(i);
            }
        }
        if (!positions.isEmpty()) {
            splitPoint = positions.get(0);
            int bestDist = Math.abs(splitPoint - mid);
            for (int i = 1; i < positions.size(); i++) {
                int pos = positions.get(i);
                int dist = Math.abs(pos - mid);
                if (dist < bestDist) {
                    bestDist = dist;
                    splitPoint = pos;
                }
            }
        }

        // b) "; ", ": "
        if (splitPoint == -1) {
            positions.clear();
            for (int i = 1; i < length; i++) {
                char prev = text.charAt(i - 1);
                char curr = text.charAt(i);
                if (curr == ' ' && (prev == ';' || prev == ':')) {
                    positions.add(i);
                }
            }
            if (!positions.isEmpty()) {
                splitPoint = positions.get(0);
                int bestDist = Math.abs(splitPoint - mid);
                for (int i = 1; i < positions.size(); i++) {
                    int pos = positions.get(i);
                    int dist = Math.abs(pos - mid);
                    if (dist < bestDist) {
                        bestDist = dist;
                        splitPoint = pos;
                    }
                }
            }
        }

        // c) ", "
        if (splitPoint == -1) {
            positions.clear();
            for (int i = 1; i < length; i++) {
                char prev = text.charAt(i - 1);
                char curr = text.charAt(i);
                if (curr == ' ' && prev == ',') {
                    positions.add(i);
                }
            }
            if (!positions.isEmpty()) {
                splitPoint = positions.get(0);
                int bestDist = Math.abs(splitPoint - mid);
                for (int i = 1; i < positions.size(); i++) {
                    int pos = positions.get(i);
                    int dist = Math.abs(pos - mid);
                    if (dist < bestDist) {
                        bestDist = dist;
                        splitPoint = pos;
                    }
                }
            }
        }

        // d) closest space
        if (splitPoint == -1) {
            positions.clear();
            for (int i = 0; i < length; i++) {
                if (text.charAt(i) == ' ') {
                    positions.add(i);
                }
            }
            if (!positions.isEmpty()) {
                splitPoint = positions.get(0);
                int bestDist = Math.abs(splitPoint - mid);
                for (int i = 1; i < positions.size(); i++) {
                    int pos = positions.get(i);
                    int dist = Math.abs(pos - mid);
                    if (dist < bestDist) {
                        bestDist = dist;
                        splitPoint = pos;
                    }
                }
            }
        }

        // e) no space at all
        if (splitPoint == -1) {
            splitPoint = mid;
        }

        String left = text.substring(0, splitPoint).trim();
        String right = text.substring(splitPoint).trim();

        List<String> result = new ArrayList<>();
        if (!left.isEmpty()) {
            result.addAll(splitLongParagraph(left, maxChars));
        }
        if (!right.isEmpty()) {
            result.addAll(splitLongParagraph(right, maxChars));
        }
        return result;
    }
}
