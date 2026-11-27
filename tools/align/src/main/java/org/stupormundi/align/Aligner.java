package org.stupormundi.align;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the transcript words of one audio segment into one cue per matched paragraph.
 *
 * <p>The segment's anchor paragraph (the chapter heading) is located first, so the spoken preamble
 * before it is skipped. Then the paragraphs of the content range are matched in order, each search
 * starting after the previous match. Progress and problems are reported on stderr.
 */
final class Aligner {

    /** Number of following paragraphs whose tokens are used as lookahead when matching. */
    private static final int LOOKAHEAD_PARAGRAPHS = 2;

    /** A match may start this much (in seconds) before the end of the previous cue; earlier is rejected. */
    private static final double MAX_OVERLAP_SECONDS = 1.0;

    /** @param totalParagraphs number of paragraphs in the content range, matched or not */
    record Result(List<Cue> cues, int totalParagraphs) {}

    private Aligner() {}

    static Result align(List<Word> words, ParagraphIndex index, Segment segment) {
        Integer startIdx = index.idToIndex().get(segment.startParagraph);
        if (startIdx == null) {
            throw new AlignException("Start paragraph ID not found in ru.json: " + segment.startParagraph);
        }

        Integer contentStartIdx = index.idToIndex().get(segment.contentStartParagraph);
        Integer endIdx = index.idToIndex().get(segment.endParagraph);

        int searchPos = findSearchStart(words, index.paragraphs().get(startIdx));

        if (contentStartIdx == null) {
            throw new AlignException("contentStartParagraph ID not found in ru.json: " + segment.contentStartParagraph);
        }
        if (endIdx == null) {
            throw new AlignException("endParagraph ID not found in ru.json: " + segment.endParagraph);
        }
        if (contentStartIdx > endIdx) {
            throw new AlignException("Invalid paragraph range: contentStartParagraph comes after endParagraph.");
        }

        List<Paragraph> paragraphs = index.paragraphs();
        List<Cue> cues = new ArrayList<>();
        double lastCueEndTime = 0.0;
        for (int i = contentStartIdx; i <= endIdx; i++) {
            Paragraph p = paragraphs.get(i);
            String[] tokens = TextNormalizer.normalizeAndTokenize(p.cyr);
            String[] lookaheadTokens = lookaheadTokens(paragraphs, i, endIdx);
            FuzzyMatcher.MatchResult match = FuzzyMatcher.fuzzyMatch(tokens, lookaheadTokens, words, searchPos);
            if (match == null) {
                continue;
            }
            int first = match.firstMatchedWordIndex;
            int last = match.lastMatchedWordIndex;
            if (first < 0 || last < first || last >= words.size()) {
                continue;
            }
            double cueStart = words.get(first).start;
            if (cueStart < lastCueEndTime - MAX_OVERLAP_SECONDS) {
                System.err.println("time-rejected: " + p.id
                        + " matchStart=" + words.get(first).start
                        + " lastCueEnd=" + lastCueEndTime
                        + " firstIdx=" + first
                        + " searchPos=" + searchPos);
                continue;
            }
            cues.add(buildCue(p, words, first, last, match.wordOffset));
            searchPos = last + 1;
            lastCueEndTime = words.get(last).end;
        }

        reportUnmatched(paragraphs, contentStartIdx, endIdx, cues);
        return new Result(cues, endIdx - contentStartIdx + 1);
    }

    /** Position in the transcript just after the anchor paragraph, or 0 (with a warning) if it is not found. */
    private static int findSearchStart(List<Word> words, Paragraph anchor) {
        String[] anchorTokens = TextNormalizer.normalizeAndTokenize(anchor.cyr);
        FuzzyMatcher.MatchResult anchorMatch = FuzzyMatcher.fuzzyMatch(anchorTokens, new String[0], words, 0);
        if (anchorMatch != null) {
            return anchorMatch.lastMatchedWordIndex + 1;
        }
        System.err.println("Warning: anchor paragraph not matched, starting search from position 0.");
        return 0;
    }

    /** Tokens of the next paragraphs inside the content range, used only to anchor the match. */
    private static String[] lookaheadTokens(List<Paragraph> paragraphs, int index, int endIdx) {
        List<String> tokens = new ArrayList<>();
        for (int offset = 1; offset <= LOOKAHEAD_PARAGRAPHS; offset++) {
            int idx = index + offset;
            if (idx > endIdx) {
                break;
            }
            tokens.addAll(Arrays.asList(TextNormalizer.normalizeAndTokenize(paragraphs.get(idx).cyr)));
        }
        return tokens.toArray(new String[0]);
    }

    private static Cue buildCue(Paragraph paragraph, List<Word> words, int first, int last, int wordOffset) {
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
        cue.paragraphId = paragraph.id;
        cue.start = words.get(first).start;
        cue.end = words.get(last).end;
        cue.wordOffset = wordOffset;
        cue.words = cueWords;
        return cue;
    }

    private static void reportUnmatched(List<Paragraph> paragraphs, int from, int to, List<Cue> cues) {
        Set<String> matchedParagraphIds = new HashSet<>();
        for (Cue cue : cues) {
            matchedParagraphIds.add(cue.paragraphId);
        }
        for (int i = from; i <= to; i++) {
            Paragraph p = paragraphs.get(i);
            if (!matchedParagraphIds.contains(p.id)) {
                System.err.println("unmatched: " + p.id + " - " + p.cyr.substring(0, Math.min(60, p.cyr.length())));
            }
        }
    }
}
