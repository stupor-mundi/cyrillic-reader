package org.stupormundi.textprep;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Splits cleaned text into paragraphs and breaks overlong paragraphs at natural boundaries. */
final class ParagraphSplitter {

    /** One or more empty lines. */
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n+");

    /**
     * Characters that may end a clause, in order of preference. A split is allowed after one of these
     * when it is followed by a space.
     */
    private static final String[] BOUNDARY_TIERS = {".?!", ";:", ","};

    private ParagraphSplitter() {
    }

    /**
     * Splits text on empty lines, trims and drops empty paragraphs, and splits every paragraph longer
     * than {@code maxChars}.
     */
    static List<String> splitIntoParagraphs(String text, int maxChars) {
        List<String> paragraphs = new ArrayList<>();
        for (String raw : PARAGRAPH_BREAK.split(text)) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.addAll(splitLongParagraph(trimmed, maxChars));
            }
        }
        return paragraphs;
    }

    /**
     * Splits a paragraph longer than {@code maxChars} into two near the middle, recursively until every
     * part fits. The split point is the boundary closest to the middle from the first tier that has any:
     * sentence end, then {@code ;} or {@code :}, then {@code ,}, then any space, then the exact middle.
     */
    static List<String> splitLongParagraph(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }

        int splitPoint = findSplitPoint(text);
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

    private static int findSplitPoint(String text) {
        int middle = text.length() / 2;

        for (String boundaryChars : BOUNDARY_TIERS) {
            List<Integer> positions = new ArrayList<>();
            for (int i = 1; i < text.length(); i++) {
                if (text.charAt(i) == ' ' && boundaryChars.indexOf(text.charAt(i - 1)) >= 0) {
                    positions.add(i);
                }
            }
            int closest = closestTo(positions, middle);
            if (closest != -1) {
                return closest;
            }
        }

        List<Integer> spaces = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                spaces.add(i);
            }
        }
        int closestSpace = closestTo(spaces, middle);
        return closestSpace != -1 ? closestSpace : middle;
    }

    /** Returns the position closest to {@code target} (the earliest on a tie), or -1 if there is none. */
    private static int closestTo(List<Integer> positions, int target) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int position : positions) {
            int distance = Math.abs(position - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = position;
            }
        }
        return best;
    }
}
