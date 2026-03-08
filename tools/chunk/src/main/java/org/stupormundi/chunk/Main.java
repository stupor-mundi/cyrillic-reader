package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Main {

    // Hardcoded defaults for v1
    private static final int RU_CONSUME_TARGET = 60;
    private static final int RU_CONSUME_MIN = 55;
    private static final int RU_CONSUME_MAX = 65;
    private static final int RU_OVERLAP = 10;
    private static final int EN_START_OVERLAP = 15;
    private static final int EN_LOOKAHEAD = 120;
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 4000;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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

        RuBook ruBook = readRuBook(ruPath);
        EnBook enBook = readEnBook(enPath);

        if (!"ru".equalsIgnoreCase(ruBook.lang)) {
            System.err.println("Warning: ru.json lang is '" + ruBook.lang + "', expected 'ru'. Continuing.");
        }
        if (!"en".equalsIgnoreCase(enBook.lang)) {
            System.err.println("Warning: en.json lang is '" + enBook.lang + "', expected 'en'. Continuing.");
        }

        if (ruBook.paragraphs == null || ruBook.paragraphs.isEmpty()) {
            throw new IllegalStateException("ru.json has no paragraphs.");
        }
        if (enBook.paragraphs == null || enBook.paragraphs.isEmpty()) {
            throw new IllegalStateException("en.json has no paragraphs.");
        }

        Map<String, Integer> enIndexById = buildEnIndex(enBook.paragraphs);
        Map<String, Integer> ruIndexById = buildRuIndex(ruBook.paragraphs);

        List<Chunk> allChunks = new ArrayList<>();

        int ruPtr = 0;
        int enPtr = 0;
        int windowIndex = 1;
        int nextChunkNumeric = 1;

        while (ruPtr < ruBook.paragraphs.size()) {
            int remainingRu = ruBook.paragraphs.size() - ruPtr;
            int ruCandidatesCount = Math.min(RU_CONSUME_MAX, remainingRu);
            List<RuParagraph> ruCandidates = ruBook.paragraphs.subList(ruPtr, ruPtr + ruCandidatesCount);

            int ruContextStart = Math.max(0, ruPtr - RU_OVERLAP);
            List<RuParagraph> ruContext = ruBook.paragraphs.subList(ruContextStart, ruPtr);

            int enWindowStart = Math.max(0, enPtr - EN_START_OVERLAP);
            int enWindowEnd = Math.min(enBook.paragraphs.size(), enPtr + EN_LOOKAHEAD);
            List<EnParagraph> enWindow = enBook.paragraphs.subList(enWindowStart, enWindowEnd);

            String startingChunkId = formatChunkId(nextChunkNumeric);

            int windowRuStart = ruPtr;

            String basePrompt = buildPrompt(startingChunkId, ruContext, ruCandidates, enWindow);

            ChunkWindowResponse response = callAnthropicWithValidation(
                    apiKey,
                    basePrompt,
                    startingChunkId,
                    ruCandidates,
                    enWindow,
                    enIndexById
            );

            if (response.chunks == null) {
                throw new IllegalStateException("LLM returned null chunks list.");
            }

            allChunks.addAll(response.chunks);

            int producedChunks = response.chunks.size();
            nextChunkNumeric += producedChunks;

            // Advance pointers
            Integer lastIdx = ruIndexById.get(response.lastRuCovered);
            if (lastIdx == null) {
                throw new IllegalStateException("lastRuCovered '" + response.lastRuCovered + "' not found in ru index.");
            }
            int coveredCount = lastIdx - windowRuStart + 1;
            ruPtr = lastIdx + 1;
            if (response.lastEnUsed != null && !response.lastEnUsed.isBlank()) {
                Integer idx = enIndexById.get(response.lastEnUsed);
                if (idx != null) {
                    enPtr = idx + 1;
                }
            }

            int ruHumanStart = windowRuStart + 1;
            int ruHumanEnd = lastIdx + 1;
            System.err.printf(
                    "Window %d: ru %d-%d (covered %d), enPtr=%d, chunks=%d%n",
                    windowIndex,
                    ruHumanStart,
                    ruHumanEnd,
                    coveredCount,
                    enPtr,
                    producedChunks
            );

            windowIndex++;
        }

        // Prepare output structure
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("bookId", bookId);
        output.put("chunks", allChunks);

        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent());
        }

        ObjectMapper prettyMapper = MAPPER.copy().enable(SerializationFeature.INDENT_OUTPUT);
        prettyMapper.writeValue(outPath.toFile(), output);
    }

    private static RuBook readRuBook(Path ruPath) throws IOException {
        return MAPPER.readValue(ruPath.toFile(), RuBook.class);
    }

    private static EnBook readEnBook(Path enPath) throws IOException {
        return MAPPER.readValue(enPath.toFile(), EnBook.class);
    }

    private static Map<String, Integer> buildEnIndex(List<EnParagraph> paragraphs) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            EnParagraph p = paragraphs.get(i);
            if (p.id == null || p.id.isBlank()) {
                throw new IllegalStateException("en paragraph at index " + i + " has empty id.");
            }
            if (index.put(p.id, i) != null) {
                throw new IllegalStateException("Duplicate en id detected: " + p.id);
            }
        }
        return index;
    }

    private static Map<String, Integer> buildRuIndex(List<RuParagraph> paragraphs) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            RuParagraph p = paragraphs.get(i);
            if (p.id == null || p.id.isBlank()) {
                throw new IllegalStateException("ru paragraph at index " + i + " has empty id.");
            }
            if (index.put(p.id, i) != null) {
                throw new IllegalStateException("Duplicate ru id detected: " + p.id);
            }
        }
        return index;
    }

    private static String formatChunkId(int numeric) {
        return String.format("c%06d", numeric);
    }

    /**
     * Build the strict prompt string for a window.
     */
    static String buildPrompt(
            String startingChunkId,
            List<RuParagraph> ruContext,
            List<RuParagraph> ruCandidates,
            List<EnParagraph> enWindow
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are a strict JSON-only segmentation engine.

                Task:
                - Segment the Russian CANDIDATES paragraphs into aligned chunks.
                - Each chunk must reference:
                  - One or more Russian paragraph IDs from the CANDIDATES list.
                  - Zero or more English paragraph IDs from the English window.

                Output format (JSON ONLY, no extra text, no comments, no code fences):
                {
                  "chunks": [
                    {
                      "id": "c000001",
                      "ru": ["ru-000001", "ru-000002"],
                      "en": ["en-000003", "en-000004"]
                    }
                  ],
                  "lastEnUsed": "en-000004",
                  "lastRuCovered": "ru-000062"
                }

                Rules:
                - Use chunk IDs starting from the provided starting chunk ID and increment sequentially by 1.
                - Use ONLY Russian paragraph IDs from the Russian CANDIDATES section (not from CONTEXT ONLY).
                - Cover between 55 and 65 Russian paragraphs, stopping at the most natural semantic boundary within that range.
                - Report the last Russian paragraph ID you covered in "lastRuCovered".
                - Every Russian paragraph you include in any chunk must be covered sequentially without gaps from the first candidate paragraph.
                - Do NOT introduce any Russian IDs that are not listed in the CANDIDATES section.
                - Use ONLY English paragraph IDs from the English window section.
                - English paragraph IDs must appear in non-decreasing order as they occur in the book.
                - lastEnUsed must be exactly the English paragraph ID with the largest numeric suffix among all used English IDs.
                - If you do not use any English paragraphs at all, set lastEnUsed to an empty string "".
                - The JSON must be syntactically valid and parseable.
                - Do not include any explanations, prose, or formatting outside the JSON object.

                """);

        sb.append("Starting chunk ID:\n");
        sb.append(startingChunkId).append("\n\n");

        sb.append("Russian CONTEXT ONLY:\n");
        if (ruContext.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (RuParagraph p : ruContext) {
                sb.append(p.id).append(": ").append(sanitizeLine(p.cyr)).append("\n");
            }
        }
        sb.append("\n");

        sb.append("Russian CANDIDATES (target ~60, minimum 55, maximum 65):\n");
        for (RuParagraph p : ruCandidates) {
            sb.append(p.id).append(": ").append(sanitizeLine(p.cyr)).append("\n");
        }
        sb.append("\n");

        sb.append("English WINDOW INCLUDING CONTEXT AND LOOKAHEAD:\n");
        if (enWindow.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (EnParagraph p : enWindow) {
                sb.append(p.id).append(": ").append(sanitizeLine(p.text)).append("\n");
            }
        }

        return sb.toString();
    }

    private static String sanitizeLine(String text) {
        if (text == null) {
            return "";
        }
        // Collapse newlines and trim to keep the prompt compact and single-line per paragraph.
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static ChunkWindowResponse callAnthropicWithValidation(
            String apiKey,
            String basePrompt,
            String startingChunkId,
            List<RuParagraph> ruCandidates,
            List<EnParagraph> enWindow,
            Map<String, Integer> enIndexById
    ) throws IOException, InterruptedException {
        String[] retryMessages = {
                "Your previous output was invalid. Output JSON only and obey all constraints.",
                "INVALID OUTPUT. Every RU paragraph in MUST-COVER must appear in EXACTLY ONE chunk. No duplicates. No omissions. JSON only.",
                "CRITICAL: Output a single valid JSON object. Each ru-XXXXXX id from MUST-COVER appears in exactly one chunk. No exceptions."
        };
        IllegalArgumentException lastException = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            String prompt = attempt == 0 ? basePrompt : basePrompt + "\n\n" + retryMessages[attempt - 1] + "\n";
            ChunkWindowResponse response = callAnthropic(apiKey, prompt);
            try {
                validateWindowResponse(response, startingChunkId, ruCandidates, enWindow, enIndexById);
                return response;
            } catch (IllegalArgumentException e) {
                System.err.println("Attempt " + (attempt+1) + " validation error: " + e.getMessage());
                lastException = e;
            }
        }
        throw lastException;
    }

    private static ChunkWindowResponse callAnthropic(String apiKey, String prompt)
            throws IOException, InterruptedException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", MODEL);
        root.put("max_tokens", MAX_TOKENS);

        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        String body = MAPPER.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ANTHROPIC_URL))
                .timeout(Duration.ofMinutes(2))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

//        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpResponse<String> response = sendWith429Retry(request);

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Anthropic API returned status " + response.statusCode() + ": " + response.body());
        }

        var rootNode = MAPPER.readTree(response.body());
        var contentNode = rootNode.get("content");
        if (contentNode == null || !contentNode.isArray() || contentNode.isEmpty()) {
            throw new IllegalStateException("Anthropic response missing 'content' array.");
        }
        var first = contentNode.get(0);
        if (first == null) {
            throw new IllegalStateException("Anthropic response content[0] is null.");
        }

        String text;
        if (first.hasNonNull("text")) {
            text = first.get("text").asText();
        } else {
            throw new IllegalStateException("Anthropic response content[0] has no 'text' field.");
        }

        String jsonPayload = extractJsonPayload(text);
        System.err.println("Attempt raw response (first 500 chars): " +
                jsonPayload.substring(0, Math.min(500, jsonPayload.length())));
        try {
            return MAPPER.readValue(jsonPayload, ChunkWindowResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse LLM JSON payload: " + e.getMessage() + "\nPayload:\n" + jsonPayload, e);
        }
    }

    private static String extractJsonPayload(String text) {
        if (text == null) {
            throw new IllegalStateException("LLM returned null text content.");
        }
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1).trim();
        }
        // Fallback: hope the whole thing is JSON
        return trimmed;
    }

    private static void validateWindowResponse(
            ChunkWindowResponse response,
            String expectedStartingChunkId,
            List<RuParagraph> ruCandidates,
            List<EnParagraph> enWindow,
            Map<String, Integer> enIndexById
    ) {
        if (response == null) {
            throw new IllegalArgumentException("Response is null.");
        }
        if (response.chunks == null || response.chunks.isEmpty()) {
            throw new IllegalArgumentException("Response has no chunks.");
        }
        if (response.lastRuCovered == null || response.lastRuCovered.isBlank()) {
            throw new IllegalArgumentException("Response missing lastRuCovered.");
        }

        // Resolve covered set: from first candidate up to and including lastRuCovered
        Set<String> ruCandidateIds = new HashSet<>();
        List<String> ruCandidateIdOrder = new ArrayList<>();
        for (RuParagraph p : ruCandidates) {
            if (p.id == null || p.id.isBlank()) {
                throw new IllegalArgumentException("RU candidate paragraph has empty id.");
            }
            ruCandidateIds.add(p.id);
            ruCandidateIdOrder.add(p.id);
        }
        if (!ruCandidateIds.contains(response.lastRuCovered)) {
            throw new IllegalArgumentException("lastRuCovered '" + response.lastRuCovered + "' is not in candidates.");
        }
        int lastCoveredPosition = ruCandidateIdOrder.indexOf(response.lastRuCovered);
        Set<String> ruCoveredIds = new HashSet<>(ruCandidateIdOrder.subList(0, lastCoveredPosition + 1));
        int coveredCount = ruCoveredIds.size();
        boolean isLastWindow = (lastCoveredPosition == ruCandidates.size() - 1);
        if (!isLastWindow && (coveredCount < RU_CONSUME_MIN || coveredCount > RU_CONSUME_MAX)) {
            throw new IllegalArgumentException("Covered RU count " + coveredCount +
                    " is outside allowed range [" + RU_CONSUME_MIN + ", " + RU_CONSUME_MAX + "].");
        }

        // 1) chunk IDs sequential starting at expected startingChunkId
        int expectedNumeric = parseChunkNumeric(expectedStartingChunkId);
        for (int i = 0; i < response.chunks.size(); i++) {
            Chunk chunk = response.chunks.get(i);
            if (chunk.id == null || chunk.id.isBlank()) {
                throw new IllegalArgumentException("Chunk at index " + i + " has empty id.");
            }
            int numeric = parseChunkNumeric(chunk.id);
            if (numeric != expectedNumeric) {
                throw new IllegalArgumentException("Chunk id sequence mismatch at index " + i +
                        ": expected " + formatChunkId(expectedNumeric) + " but got " + chunk.id);
            }
            expectedNumeric++;
        }

        Set<String> enWindowIds = new HashSet<>();
        for (EnParagraph p : enWindow) {
            if (p.id == null || p.id.isBlank()) {
                throw new IllegalArgumentException("EN window paragraph has empty id.");
            }
            enWindowIds.add(p.id);
        }

        // RU: every paragraph from first candidate to lastRuCovered appears in exactly one chunk (no gaps, no duplicates)
        Map<String, Integer> ruCounts = new HashMap<>();
        for (String id : ruCoveredIds) {
            ruCounts.put(id, 0);
        }

        // EN ids subset of provided EN window and monotonic
        int lastEnIndex = -1;
        List<String> allEnIdsUsed = new ArrayList<>();

        for (int i = 0; i < response.chunks.size(); i++) {
            Chunk chunk = response.chunks.get(i);

            if (chunk.ru == null || chunk.ru.isEmpty()) {
                throw new IllegalArgumentException("Chunk " + chunk.id + " has empty 'ru' list.");
            }

            for (String ruId : chunk.ru) {
                if (!ruCandidateIds.contains(ruId)) {
                    throw new IllegalArgumentException("Chunk " + chunk.id + " references RU id outside candidates: " + ruId);
                }
                if (ruCoveredIds.contains(ruId)) {
                    ruCounts.merge(ruId, 1, Integer::sum);
                }
                // RU ids after lastRuCovered are not allowed in chunks
                if (ruCandidateIdOrder.indexOf(ruId) > lastCoveredPosition) {
                    throw new IllegalArgumentException("Chunk " + chunk.id + " references RU id " + ruId +
                            " which is after lastRuCovered.");
                }
            }

            if (chunk.en != null) {
                for (String enId : chunk.en) {
                    if (!enWindowIds.contains(enId)) {
                        throw new IllegalArgumentException("Chunk " + chunk.id + " references EN id outside window: " + enId);
                    }
                    Integer idx = enIndexById.get(enId);
                    if (idx == null) {
                        throw new IllegalArgumentException("EN id not found in full list: " + enId);
                    }
                    if (idx < lastEnIndex) {
                        throw new IllegalArgumentException("EN ids are not monotonic: " + enId +
                                " appears before previously used EN paragraph.");
                    }
                    if (idx > lastEnIndex) {
                        lastEnIndex = idx;
                    }
                    allEnIdsUsed.add(enId);
                }
            }
        }

        // Check RU covered: each must appear exactly once
        for (Map.Entry<String, Integer> e : ruCounts.entrySet()) {
            if (e.getValue() == 0) {
                throw new IllegalArgumentException("RU paragraph " + e.getKey() + " not covered by any chunk.");
            }
            if (e.getValue() > 1) {
                throw new IllegalArgumentException("RU paragraph " + e.getKey() +
                        " appears in multiple chunks (" + e.getValue() + " times).");
            }
        }

        // 6) lastEnUsed equals max EN id referenced (by numeric suffix compare)
        if (allEnIdsUsed.isEmpty()) {
            if (response.lastEnUsed != null && !response.lastEnUsed.isBlank()) {
                throw new IllegalArgumentException("No EN ids used but lastEnUsed is '" + response.lastEnUsed + "'. Expected empty.");
            }
        } else {
            String maxEnId = null;
            int maxSuffix = -1;
            for (String enId : allEnIdsUsed) {
                int suffix = extractNumericSuffix(enId);
                if (suffix > maxSuffix) {
                    maxSuffix = suffix;
                    maxEnId = enId;
                }
            }
            if (!Objects.equals(maxEnId, response.lastEnUsed)) {
                throw new IllegalArgumentException("lastEnUsed mismatch: expected " + maxEnId +
                        " but got " + response.lastEnUsed);
            }
        }
    }

    private static int parseChunkNumeric(String chunkId) {
        if (chunkId == null || chunkId.length() < 2 || chunkId.charAt(0) != 'c') {
            throw new IllegalArgumentException("Invalid chunk id format: " + chunkId);
        }
        String numericPart = chunkId.substring(1);
        try {
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid chunk id numeric part: " + chunkId, e);
        }
    }

    private static int extractNumericSuffix(String id) {
        if (id == null || id.isEmpty()) {
            return -1;
        }
        int i = id.length() - 1;
        while (i >= 0 && Character.isDigit(id.charAt(i))) {
            i--;
        }
        if (i == id.length() - 1) {
            return -1;
        }
        String digits = id.substring(i + 1);
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static HttpResponse<String> sendWith429Retry(HttpRequest request) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 429) {
                return resp;
            }
            attempt++;
            // Exponential backoff: 15s, 30s, 60s, then cap at 60s
            long sleepMs = Math.min(60_000L, 15_000L * (1L << Math.min(attempt - 1, 2)));
            System.err.println("Rate limited (429). Sleeping " + (sleepMs / 1000) + "s then retrying...");
            Thread.sleep(sleepMs);
        }
    }


    // Data classes

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RuBook {
        public String lang;
        public List<RuParagraph> paragraphs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EnBook {
        public String lang;
        public List<EnParagraph> paragraphs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class RuParagraph {
        public String id;
        public String cyr;
        public String lat;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EnParagraph {
        public String id;
        public String text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Chunk {
        public String id;
        public List<String> ru;
        public List<String> en;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChunkWindowResponse {
        public List<Chunk> chunks;
        public String lastEnUsed;
        public String lastRuCovered;
    }
}

