package org.stupormundi.chunk;

import java.util.List;

/** Builds the strict prompt for one window. */
final class PromptBuilder {

    private PromptBuilder() {
    }

    static String build(
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

    /** Collapses newlines and trims, to keep the prompt compact with one line per paragraph. */
    private static String sanitizeLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
