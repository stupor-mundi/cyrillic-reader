package org.stupormundi.align;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads the three input files: the transcript words, ru.json and audio_index.json. */
final class InputLoader {

    private InputLoader() {}

    static void ensureFileExists(Path path, String messagePrefix) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException(messagePrefix + path.toAbsolutePath());
        }
    }

    static List<Word> loadWords(ObjectMapper mapper, Path wordsPath) throws IOException {
        List<Word> rawWords = mapper.readValue(wordsPath.toFile(), new TypeReference<List<Word>>() {});
        List<Word> words = new ArrayList<>();
        for (Word w : rawWords) {
            if (w == null || w.word == null) {
                continue;
            }
            String norm = TextNormalizer.normalizeText(w.word);
            if (norm.isEmpty()) {
                continue;
            }
            w.norm = norm;
            words.add(w);
        }
        return words;
    }

    static ParagraphIndex loadParagraphs(ObjectMapper mapper, Path ruPath) throws IOException {
        JsonNode root = mapper.readTree(ruPath.toFile());
        JsonNode paragraphsNode;
        if (root.isArray()) {
            paragraphsNode = root;
        } else {
            paragraphsNode = root.get("paragraphs");
        }
        if (paragraphsNode == null || !paragraphsNode.isArray()) {
            throw new IOException("Invalid ru.json: expected 'paragraphs' array or top-level array.");
        }

        List<Paragraph> paragraphs = new ArrayList<>();
        Map<String, Integer> idToIndex = new HashMap<>();
        int index = 0;
        for (JsonNode node : paragraphsNode) {
            String id = textOrNull(node.get("id"));
            String cyr = textOrNull(node.get("cyr"));
            if (id == null) {
                continue;
            }
            Paragraph p = new Paragraph();
            p.id = id;
            p.cyr = cyr == null ? "" : cyr;
            paragraphs.add(p);
            idToIndex.put(id, index);
            index++;
        }

        return new ParagraphIndex(paragraphs, idToIndex);
    }

    static Segment loadSegment(ObjectMapper mapper, Path audioIndexPath, String segmentId)
            throws IOException {
        JsonNode root = mapper.readTree(audioIndexPath.toFile());
        JsonNode segmentsNode;
        if (root.isArray()) {
            segmentsNode = root;
        } else {
            segmentsNode = root.get("segments");
        }

        if (segmentsNode == null || !segmentsNode.isArray()) {
            throw new IOException("Invalid audio_index.json: expected 'segments' array or top-level array.");
        }

        for (JsonNode node : segmentsNode) {
            String id = textOrNull(node.get("id"));
            if (Objects.equals(id, segmentId)) {
                Segment s = new Segment();
                s.id = id;
                s.audioFile = textOrNull(node.get("audioFile"));
                s.startParagraph = textOrNull(node.get("startParagraph"));
                s.contentStartParagraph = textOrNull(node.get("contentStartParagraph"));
                s.endParagraph = textOrNull(node.get("endParagraph"));

                if (s.audioFile == null || s.startParagraph == null || s.contentStartParagraph == null || s.endParagraph == null) {
                    throw new IOException("Segment " + segmentId + " is missing required fields in audio_index.json.");
                }
                return s;
            }
        }

        throw new AlignException("Segment not found in audio_index.json for segmentId: " + segmentId);
    }

    static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
