package org.stupormundi.align;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

public class Main {

    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance(2);

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar align.jar <dataDir> <bookId> <segmentId>");
            System.exit(1);
        }

        String dataDir = args[0];
        String bookId = args[1];
        String segmentId = args[2];

        Path baseDir = Paths.get(dataDir, bookId);
        Path wordsPath = baseDir.resolve(Paths.get("words", segmentId + ".json"));
        Path ruPath = baseDir.resolve("ru.json");
        Path audioIndexPath = baseDir.resolve("audio_index.json");
        Path outputPath = baseDir.resolve(Paths.get("cues", segmentId + ".json"));

        try {
            ensureFileExists(wordsPath, "Words file not found: ");
            ensureFileExists(ruPath, "ru.json not found: ");
            ensureFileExists(audioIndexPath, "audio_index.json not found: ");
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);

        try {
            // Step 1 - Load words JSON
            List<Word> words = loadWords(mapper, wordsPath);

            // Step 2 - Load ru.json
            ParagraphsData paragraphsData = loadParagraphs(mapper, ruPath);

            // Step 3 - Load audio_index.json
            Segment segment = loadSegment(mapper, audioIndexPath, segmentId);

            // Step 4 - Anchor search
            Integer startIdx = paragraphsData.idToIndex.get(segment.startParagraph);
            if (startIdx == null) {
                System.err.println("Start paragraph ID not found in ru.json: " + segment.startParagraph);
                System.exit(1);
            }
            Paragraph startParagraph = paragraphsData.paragraphs.get(startIdx);

            String[] anchorTokens = normalizeAndTokenize(startParagraph.cyr);
            int searchPos;
            MatchResult anchorMatch = fuzzyMatch(anchorTokens, new String[0], words, 0, false);
            if (anchorMatch != null) {
                searchPos = anchorMatch.lastMatchedWordIndex + 1;
            } else {
                System.err.println("Warning: anchor paragraph not matched, starting search from position 0.");
                searchPos = 0;
            }

            // Step 5 - Paragraph matching
            Integer contentStartIdx = paragraphsData.idToIndex.get(segment.contentStartParagraph);
            Integer endIdx = paragraphsData.idToIndex.get(segment.endParagraph);
            if (contentStartIdx == null) {
                System.err.println("contentStartParagraph ID not found in ru.json: " + segment.contentStartParagraph);
                System.exit(1);
            }
            if (endIdx == null) {
                System.err.println("endParagraph ID not found in ru.json: " + segment.endParagraph);
                System.exit(1);
            }
            if (contentStartIdx > endIdx) {
                System.err.println("Invalid paragraph range: contentStartParagraph comes after endParagraph.");
                System.exit(1);
            }

            List<Cue> cues = new ArrayList<>();
            int totalInSlice = endIdx - contentStartIdx + 1;
            double lastCueEndTime = 0.0;
            for (int i = contentStartIdx; i <= endIdx; i++) {
                Paragraph p = paragraphsData.paragraphs.get(i);
                String[] tokens = normalizeAndTokenize(p.cyr);
                List<String> lookaheadList = new ArrayList<>();
                for (int offset = 1; offset <= 2; offset++) {
                    int idx = i + offset;
                    if (idx > endIdx) {
                        break;
                    }
                    Paragraph nextParagraph = paragraphsData.paragraphs.get(idx);
                    String[] nextTokens = normalizeAndTokenize(nextParagraph.cyr);
                    if (nextTokens.length > 0) {
                        lookaheadList.addAll(Arrays.asList(nextTokens));
                    }
                }
                String[] lookaheadTokens = lookaheadList.toArray(new String[0]);
                MatchResult match = fuzzyMatch(tokens, lookaheadTokens, words, searchPos, "ru-000023".equals(p.id));
                if (match != null) {
                    int first = match.firstMatchedWordIndex;
                    int last = match.lastMatchedWordIndex;
                    if (first >= 0 && last >= first && last < words.size()) {
                        double cueStart = words.get(first).start;
                        if (cueStart < lastCueEndTime - 1.0) {
                            System.err.println("time-rejected: " + p.id + 
                                " matchStart=" + words.get(first).start + 
                                " lastCueEnd=" + lastCueEndTime +
                                " firstIdx=" + first +
                                " searchPos=" + searchPos);
                        } else {
                            List<CueWord> cueWords = new ArrayList<>();
                            for (int w = first; w <= last; w++) {
                                Word word = words.get(w);
                                CueWord cw = new CueWord();
                                cw.text = word.word;
                                cw.start = word.start;
                                cw.end = word.end;
                                cueWords.add(cw);
                            }

                            Cue cue = new Cue();
                            cue.paragraphId = p.id;
                            cue.start = words.get(first).start;
                            cue.end = words.get(last).end;
                            cue.words = cueWords;
                            cues.add(cue);

                            searchPos = last + 1;

                            System.err.println("searchPos after " + p.id + ": " + searchPos + " (time=" + words.get(last).end + ")");

                            lastCueEndTime = words.get(last).end;
                        }
                    }
                }
            }

            // Diagnostics: unmatched paragraphs in slice
            Set<String> matchedParagraphIds = new HashSet<>();
            for (Cue cue : cues) {
                matchedParagraphIds.add(cue.paragraphId);
            }
            for (int i = contentStartIdx; i <= endIdx; i++) {
                Paragraph p = paragraphsData.paragraphs.get(i);
                if (!matchedParagraphIds.contains(p.id)) {
                    System.err.println("unmatched: " + p.id + " - " + p.cyr.substring(0, Math.min(60, p.cyr.length())));
                }
            }

            // Step 6 - Write cues JSON
            CuesResult result = new CuesResult();
            result.bookId = bookId;
            result.audioFile = segment.audioFile;
            result.cues = cues;

            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), result);

            // Step 7 - Diagnostics
            System.err.println("total paragraphs in slice: " + totalInSlice);
            System.err.println("paragraphs matched: " + cues.size());
            if (!cues.isEmpty()) {
                System.err.println("first matched paragraphId: " + cues.get(0).paragraphId);
                System.err.println("last matched paragraphId: " + cues.get(cues.size() - 1).paragraphId);
            } else {
                System.err.println("first matched paragraphId: none");
                System.err.println("last matched paragraphId: none");
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void ensureFileExists(Path path, String messagePrefix) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException(messagePrefix + path.toAbsolutePath());
        }
    }

    private static List<Word> loadWords(ObjectMapper mapper, Path wordsPath) throws IOException {
        List<Word> rawWords = mapper.readValue(wordsPath.toFile(), new TypeReference<List<Word>>() {});
        List<Word> words = new ArrayList<>();
        for (Word w : rawWords) {
            if (w == null || w.word == null) {
                continue;
            }
            String norm = normalizeText(w.word);
            if (norm.isEmpty()) {
                continue;
            }
            w.norm = norm;
            words.add(w);
        }
        return words;
    }

    private static ParagraphsData loadParagraphs(ObjectMapper mapper, Path ruPath) throws IOException {
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

        ParagraphsData data = new ParagraphsData();
        data.paragraphs = paragraphs;
        data.idToIndex = idToIndex;
        return data;
    }

    private static Segment loadSegment(ObjectMapper mapper, Path audioIndexPath, String segmentId) throws IOException {
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

        System.err.println("Segment not found in audio_index.json for segmentId: " + segmentId);
        System.exit(1);
        return null; // Unreachable
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String result = text.toLowerCase(Locale.ROOT);
        result = result.replaceAll("[^\\p{L}\\s]", "");
        result = result.replaceAll("\\d", "");
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    private static String[] normalizeAndTokenize(String text) {
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }

    private static boolean isMatch(String wordNorm, String token) {
        if (wordNorm.equals(token)) return true;
        if (Math.abs(wordNorm.length() - token.length()) > 3) return false;
        return LEVENSHTEIN.apply(wordNorm, token) != -1;
    }

    private static MatchResult fuzzyMatch(String[] tokens, String[] lookaheadTokens,
                                          List<Word> words, int searchPos, boolean debug) {
        if (tokens.length == 0 || words.isEmpty() || searchPos >= words.size()) {
            return null;
        }

        int maxStart = Math.min(words.size() - 1, searchPos + 300);
        int totalTokens = tokens.length + lookaheadTokens.length;

        if (debug) {
            System.err.println("DEBUG fuzzyMatch: tokens=" + tokens.length +
                    " searchPos=" + searchPos + " maxStart=" + Math.min(words.size() - 1, searchPos + 300));
            System.err.println("DEBUG first 5 tokens: " +
                    String.join(", ", Arrays.copyOf(tokens, Math.min(5, tokens.length))));
            System.err.println("DEBUG words at searchPos: " +
                    words.get(Math.min(searchPos, words.size() - 1)).norm + " ... " +
                    words.get(Math.min(searchPos + 5, words.size() - 1)).norm);
        }

        for (int candidateStart = searchPos; candidateStart <= maxStart; candidateStart++) {
            int wordIdx = candidateStart;
            int tokenIdx = 0;
            int matchedCount = 0;
            int consecutiveMismatches = 0;
            int firstMatchedWordIndex = -1;
            int lastMatchedWordIndex = -1;

            while (tokenIdx < totalTokens && wordIdx < words.size()) {
                boolean inMainTokens = tokenIdx < tokens.length;
                String token = inMainTokens
                        ? tokens[tokenIdx]
                        : lookaheadTokens[tokenIdx - tokens.length];
                String wordNorm = words.get(wordIdx).norm;

                if (wordNorm != null && isMatch(wordNorm, token)) {
                    if (inMainTokens) {
                        if (firstMatchedWordIndex == -1) {
                            firstMatchedWordIndex = wordIdx;
                        }
                        lastMatchedWordIndex = wordIdx;
                        matchedCount++;
                    }
                    consecutiveMismatches = 0;
                    tokenIdx++;
                    wordIdx++;
                } else {
                    consecutiveMismatches++;
                    wordIdx++;
                    if (consecutiveMismatches % 3 == 0) {
                        tokenIdx++;
                    }
                    if (consecutiveMismatches > 9) {
                        break;
                    }
                }
            }

            int minRequired = Math.max(1, (int) (0.3 * tokens.length));

            if (debug && candidateStart - searchPos < 5) {
                System.err.println("DEBUG cand=" + candidateStart +
                        " matched=" + matchedCount + " needed=" + minRequired);
            }

            if (matchedCount >= minRequired && firstMatchedWordIndex != -1) {
                MatchResult result = new MatchResult();
                result.firstMatchedWordIndex = firstMatchedWordIndex;
                result.lastMatchedWordIndex = lastMatchedWordIndex;
                result.matchedCount = matchedCount;
                return result;
            }
        }

        if (debug) {
            System.err.println("DEBUG no match found for ru-000022");
        }

        return null;
    }

    // Data structures

    public static class Word {
        public String word;
        public double start;
        public double end;
        public String norm;
    }

    public static class Paragraph {
        public String id;
        public String cyr;
    }

    public static class Segment {
        public String id;
        public String audioFile;
        public String startParagraph;
        public String contentStartParagraph;
        public String endParagraph;
    }

    public static class CueWord {
        public String text;
        public double start;
        public double end;
    }

    public static class Cue {
        public String paragraphId;
        public double start;
        public double end;
        public List<CueWord> words;
    }

    public static class CuesResult {
        public String bookId;
        public String audioFile;
        public List<Cue> cues;
    }

    private static class ParagraphsData {
        List<Paragraph> paragraphs;
        Map<String, Integer> idToIndex;
    }

    private static class MatchResult {
        int firstMatchedWordIndex;
        int lastMatchedWordIndex;
        int matchedCount;
    }
}