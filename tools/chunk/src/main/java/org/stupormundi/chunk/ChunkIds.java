package org.stupormundi.chunk;

/** Chunk IDs have the form {@code c000001}: the letter c and a six-digit number. */
final class ChunkIds {

    private ChunkIds() {
    }

    static String format(int numeric) {
        return String.format("c%06d", numeric);
    }

    /**
     * Returns the number in a chunk ID.
     *
     * @throws IllegalArgumentException if the ID does not start with 'c' followed by a number
     */
    static int parseNumeric(String chunkId) {
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
}
