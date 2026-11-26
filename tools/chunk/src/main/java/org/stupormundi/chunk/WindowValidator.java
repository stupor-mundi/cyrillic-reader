package org.stupormundi.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strictly validates the LLM's response for one window. */
final class WindowValidator {

    private WindowValidator() {
    }

    /**
     * Checks that the response covers the candidates from the first one up to {@code lastRuCovered} exactly
     * once, uses sequential chunk IDs starting at {@code expectedStartingChunkId}, references only English
     * paragraphs from the window in non-decreasing order, and reports the right {@code lastEnUsed}.
     *
     * @throws IllegalArgumentException describing the first problem found
     */
    static void validate(
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
        if (!isLastWindow
                && (coveredCount < ChunkSettings.RU_CONSUME_MIN || coveredCount > ChunkSettings.RU_CONSUME_MAX)) {
            throw new IllegalArgumentException("Covered RU count " + coveredCount
                    + " is outside allowed range [" + ChunkSettings.RU_CONSUME_MIN + ", "
                    + ChunkSettings.RU_CONSUME_MAX + "].");
        }

        // 1) chunk IDs sequential starting at expected startingChunkId
        int expectedNumeric = ChunkIds.parseNumeric(expectedStartingChunkId);
        for (int i = 0; i < response.chunks.size(); i++) {
            Chunk chunk = response.chunks.get(i);
            if (chunk.id == null || chunk.id.isBlank()) {
                throw new IllegalArgumentException("Chunk at index " + i + " has empty id.");
            }
            int numeric = ChunkIds.parseNumeric(chunk.id);
            if (numeric != expectedNumeric) {
                throw new IllegalArgumentException("Chunk id sequence mismatch at index " + i
                        + ": expected " + ChunkIds.format(expectedNumeric) + " but got " + chunk.id);
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
                    throw new IllegalArgumentException("Chunk " + chunk.id + " references RU id " + ruId
                            + " which is after lastRuCovered.");
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
                        throw new IllegalArgumentException("EN ids are not monotonic: " + enId
                                + " appears before previously used EN paragraph.");
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
                throw new IllegalArgumentException("RU paragraph " + e.getKey()
                        + " appears in multiple chunks (" + e.getValue() + " times).");
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
                throw new IllegalArgumentException("lastEnUsed mismatch: expected " + maxEnId
                        + " but got " + response.lastEnUsed);
            }
        }
    }

    /** Returns the trailing number of an ID such as {@code en-000012}, or -1 if there is none. */
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
}
