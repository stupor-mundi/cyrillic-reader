package org.stupormundi.align;

import java.util.List;
import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Finds where a paragraph's tokens occur in the transcript words, tolerating recognition errors. The
 * aligner uses it only to locate the heading (anchor) paragraph; the content is aligned by {@link WordTimer}.
 */
final class FuzzyMatcher {

    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance(2);

    private FuzzyMatcher() {}

    static boolean isMatch(String wordNorm, String token) {
        if (wordNorm.equals(token)) return true;
        if (Math.abs(wordNorm.length() - token.length()) > 3) return false;
        return LEVENSHTEIN.apply(wordNorm, token) != -1;
    }

    static MatchResult fuzzyMatch(String[] tokens, String[] lookaheadTokens,
                                          List<Word> words, int searchPos) {
        if (tokens.length == 0 || words.isEmpty() || searchPos >= words.size()) {
            return null;
        }

        int maxStart = Math.min(words.size() - 1, searchPos + 300);
        int totalTokens = tokens.length + lookaheadTokens.length;


        for (int candidateStart = searchPos; candidateStart <= maxStart; candidateStart++) {
            int wordIdx = candidateStart;
            int tokenIdx = 0;
            int matchedCount = 0;
            int consecutiveMismatches = 0;
            int firstMatchedTokenIndex = -1;
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
                            firstMatchedTokenIndex = tokenIdx;
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


            if (matchedCount >= minRequired && firstMatchedWordIndex != -1) {
                MatchResult result = new MatchResult();
                result.firstMatchedWordIndex = firstMatchedWordIndex;
                result.lastMatchedWordIndex = lastMatchedWordIndex;
                result.matchedCount = matchedCount;
                result.wordOffset = firstMatchedTokenIndex == -1 ? 0 : firstMatchedTokenIndex;
                return result;
            }
        }


        return null;
    }

    static class MatchResult {
        int firstMatchedWordIndex;
        int lastMatchedWordIndex;
        int matchedCount;
        int wordOffset;
    }
}
