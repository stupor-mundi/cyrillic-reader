package org.stupormundi.align;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the transcript words of one audio segment into one cue per matched paragraph.
 *
 * <p>The segment's anchor paragraph (the chapter heading) is located first, so the spoken preamble
 * before it is skipped. Then all words of the content range are aligned to the transcript in one
 * ordered pass and a cue is derived for each paragraph. Progress and problems are reported on stderr.
 */
final class Aligner {

    /** A paragraph gets a cue if at least this share of its words was paired with transcript words. */
    private static final double MIN_MATCH_RATIO = 0.3;

    /** Most transcript words that may sit between two paired words of a paragraph (insertions). */
    private static final int MAX_EXTRA_WORDS = 15;

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
        List<Cue> cues = matchChapter(paragraphs, contentStartIdx, endIdx, words, searchPos);
        reportUnmatched(paragraphs, contentStartIdx, endIdx, cues);
        return new Result(cues, endIdx - contentStartIdx + 1);
    }

    /**
     * Aligns all book words of the content range to the transcript words from {@code searchPos} on in
     * one order-preserving pass, then derives one cue per paragraph from the words it was paired with.
     * Because the alignment is global, a wrong match far from where a paragraph belongs cannot win, and
     * cues can never overlap or run backwards.
     */
    private static List<Cue> matchChapter(List<Paragraph> paragraphs, int from, int to, List<Word> words,
            int searchPos) {
        List<String> bookTokens = new ArrayList<>();
        List<Integer> firstTokenOfParagraph = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            firstTokenOfParagraph.add(bookTokens.size());
            for (String span : WordTimer.spans(paragraphs.get(i).cyr)) {
                String folded = WordTimer.fold(span);
                if (!folded.isEmpty()) {
                    bookTokens.add(folded);
                }
            }
        }
        firstTokenOfParagraph.add(bookTokens.size());

        String[] spoken = new String[Math.max(0, words.size() - searchPos)];
        for (int j = 0; j < spoken.length; j++) {
            spoken[j] = WordTimer.fold(words.get(searchPos + j).word);
        }
        int[] anchor = WordTimer.align(bookTokens.toArray(new String[0]), spoken);

        List<Cue> cues = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            int tokFrom = firstTokenOfParagraph.get(i - from);
            int tokTo = firstTokenOfParagraph.get(i - from + 1);
            Cue cue = cueFromAnchors(paragraphs.get(i), anchor, tokFrom, tokTo, words, searchPos);
            if (cue != null) {
                cues.add(cue);
            }
        }
        return cues;
    }

    /**
     * Builds the cue of one paragraph from the transcript words its tokens {@code [tokFrom, tokTo)} were
     * paired with, or returns null if too few were. Paired words that lie far away from the rest (a
     * spurious pairing of a common word) are ignored: only the largest group of close pairings is used.
     */
    private static Cue cueFromAnchors(Paragraph paragraph, int[] anchor, int tokFrom, int tokTo,
            List<Word> words, int searchPos) {
        int tokens = tokTo - tokFrom;
        if (tokens == 0) {
            return null;
        }
        // Split the pairings into groups: a new group starts when the transcript jumps more than
        // MAX_EXTRA_WORDS beyond what the book words in between would need. Keep the largest group.
        int bestFrom = -1;
        int bestTo = -1;
        int bestCount = 0;
        int groupFrom = -1;
        int groupCount = 0;
        int prev = -1;
        for (int t = tokFrom; t < tokTo; t++) {
            if (anchor[t] < 0) {
                continue;
            }
            if (prev >= 0 && (anchor[t] - anchor[prev]) - (t - prev) > MAX_EXTRA_WORDS) {
                groupFrom = -1;
                groupCount = 0;
            }
            if (groupFrom < 0) {
                groupFrom = t;
            }
            groupCount++;
            if (groupCount > bestCount) {
                bestCount = groupCount;
                bestFrom = groupFrom;
                bestTo = t;
            }
            prev = t;
        }
        if (bestCount == 0 || bestCount < Math.max(1, (int) (MIN_MATCH_RATIO * tokens))) {
            return null;
        }
        int first = searchPos + anchor[bestFrom];
        int last = searchPos + anchor[bestTo];
        return buildCue(paragraph, words, first, last, bestFrom - tokFrom);
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
        cue.wordStarts = roundMillis(WordTimer.wordStarts(paragraph.cyr, cueWords, cue.start, cue.end));
        return cue;
    }

    private static double[] roundMillis(double[] times) {
        double[] rounded = new double[times.length];
        for (int i = 0; i < times.length; i++) {
            rounded[i] = Math.round(times[i] * 1000.0) / 1000.0;
        }
        return rounded;
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
