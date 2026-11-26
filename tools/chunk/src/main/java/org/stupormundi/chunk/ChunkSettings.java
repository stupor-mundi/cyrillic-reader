package org.stupormundi.chunk;

/** Window sizes for the chunking run (hardcoded defaults for v1). */
final class ChunkSettings {

    /** Russian paragraphs the LLM should cover per window (the prompt text says "~60"). */
    static final int RU_CONSUME_TARGET = 60;

    /** The LLM must cover at least this many Russian paragraphs, except in the last window. */
    static final int RU_CONSUME_MIN = 55;

    /** The LLM must cover at most this many Russian paragraphs; also the number of candidates offered. */
    static final int RU_CONSUME_MAX = 65;

    /** Russian paragraphs before the window that are shown as context only. */
    static final int RU_OVERLAP = 10;

    /** English paragraphs before the English pointer that are included in the window. */
    static final int EN_START_OVERLAP = 15;

    /** English paragraphs after the English pointer that are included in the window. */
    static final int EN_LOOKAHEAD = 120;

    private ChunkSettings() {
    }
}
