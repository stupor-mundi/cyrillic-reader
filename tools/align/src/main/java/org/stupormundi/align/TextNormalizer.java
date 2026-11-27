package org.stupormundi.align;

import java.util.Locale;

/** Turns book and transcript text into comparable lowercase letter-only tokens. */
final class TextNormalizer {

    private TextNormalizer() {}

    static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String result = text.toLowerCase(Locale.ROOT);
        result = result.replaceAll("[^\\p{L}\\s]", "");
        result = result.replaceAll("\\d", "");
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    static String[] normalizeAndTokenize(String text) {
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }
}
