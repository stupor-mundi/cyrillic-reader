package org.stupormundi.chunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns Russian and English paragraphs into chunks, window by window, asking an {@link LlmClient} for
 * each window and validating the answer.
 */
final class ChunkAligner {

    private static final int MAX_ATTEMPTS = 4;

    /** Appended to the prompt on the 2nd, 3rd and 4th attempt. */
    private static final String[] RETRY_MESSAGES = {
            "Your previous output was invalid. Output JSON only and obey all constraints.",
            "INVALID OUTPUT. Every RU paragraph in MUST-COVER must appear in EXACTLY ONE chunk. No duplicates. No omissions. JSON only.",
            "CRITICAL: Output a single valid JSON object. Each ru-XXXXXX id from MUST-COVER appears in exactly one chunk. No exceptions."
    };

    private final LlmClient llm;

    ChunkAligner(LlmClient llm) {
        this.llm = llm;
    }

    /** Returns the chunks for the whole book, numbered c000001, c000002, ... */
    List<Chunk> align(RuBook ruBook, EnBook enBook) throws IOException, InterruptedException {
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
            int ruCandidatesCount = Math.min(ChunkSettings.RU_CONSUME_MAX, remainingRu);
            List<RuParagraph> ruCandidates = ruBook.paragraphs.subList(ruPtr, ruPtr + ruCandidatesCount);

            int ruContextStart = Math.max(0, ruPtr - ChunkSettings.RU_OVERLAP);
            List<RuParagraph> ruContext = ruBook.paragraphs.subList(ruContextStart, ruPtr);

            int enWindowStart = Math.max(0, enPtr - ChunkSettings.EN_START_OVERLAP);
            int enWindowEnd = Math.min(enBook.paragraphs.size(), enPtr + ChunkSettings.EN_LOOKAHEAD);
            List<EnParagraph> enWindow = enBook.paragraphs.subList(enWindowStart, enWindowEnd);

            String startingChunkId = ChunkIds.format(nextChunkNumeric);
            int windowRuStart = ruPtr;

            String basePrompt = PromptBuilder.build(startingChunkId, ruContext, ruCandidates, enWindow);

            ChunkWindowResponse response =
                    requestWindow(basePrompt, startingChunkId, ruCandidates, enWindow, enIndexById);

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

            System.err.printf(
                    "Window %d: ru %d-%d (covered %d), enPtr=%d, chunks=%d%n",
                    windowIndex,
                    windowRuStart + 1,
                    lastIdx + 1,
                    coveredCount,
                    enPtr,
                    producedChunks
            );

            windowIndex++;
        }

        return allChunks;
    }

    /**
     * Asks the model for one window, validating the answer. A reply that fails validation is requested
     * again with a stricter message appended, up to {@value #MAX_ATTEMPTS} attempts in total; the last
     * validation error is then thrown.
     *
     * <p>A reply that is not parseable JSON is not retried: it fails the whole run.
     */
    private ChunkWindowResponse requestWindow(
            String basePrompt,
            String startingChunkId,
            List<RuParagraph> ruCandidates,
            List<EnParagraph> enWindow,
            Map<String, Integer> enIndexById
    ) throws IOException, InterruptedException {
        IllegalArgumentException lastException = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String prompt = attempt == 0 ? basePrompt : basePrompt + "\n\n" + RETRY_MESSAGES[attempt - 1] + "\n";
            ChunkWindowResponse response = ResponseParser.parse(llm.complete(prompt));
            try {
                WindowValidator.validate(response, startingChunkId, ruCandidates, enWindow, enIndexById);
                return response;
            } catch (IllegalArgumentException e) {
                System.err.println("Attempt " + (attempt + 1) + " validation error: " + e.getMessage());
                lastException = e;
            }
        }
        throw lastException;
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
}
