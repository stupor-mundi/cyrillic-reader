package org.stupormundi.chunk;

import java.io.IOException;

/** Turns the model's reply text into a {@link ChunkWindowResponse}. */
final class ResponseParser {

    private ResponseParser() {
    }

    /**
     * Extracts the JSON object from the reply (from the first '{' to the last '}', which also drops any
     * code fences or prose around it) and parses it.
     *
     * @throws IllegalStateException if the reply is null or the JSON cannot be parsed
     */
    static ChunkWindowResponse parse(String text) {
        String jsonPayload = extractJsonPayload(text);
        System.err.println("Attempt raw response (first 500 chars): "
                + jsonPayload.substring(0, Math.min(500, jsonPayload.length())));
        try {
            return Json.MAPPER.readValue(jsonPayload, ChunkWindowResponse.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse LLM JSON payload: " + e.getMessage() + "\nPayload:\n" + jsonPayload, e);
        }
    }

    static String extractJsonPayload(String text) {
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
}
