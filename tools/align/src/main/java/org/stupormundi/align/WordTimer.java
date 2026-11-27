package org.stupormundi.align;

import java.util.List;
import java.util.Locale;

/**
 * Gives every displayed word of a paragraph a start time.
 *
 * <p>The web reader shows a paragraph as one span per whitespace-separated word of {@code cyr}
 * (including standalone dashes). The transcript words of a cue do not line up with those spans
 * one to one: dashes are never spoken, and words are dropped, merged or split. So the book words
 * are aligned to the transcript words with an order-preserving alignment, and spans without a
 * heard counterpart get an interpolated time.
 */
final class WordTimer {

    /** Score of an exactly equal word pair; a fuzzy pair scores 1, so exact pairs win ties. */
    private static final int EXACT = 2;
    private static final int FUZZY = 1;

    /** Upper bound on the size of the alignment table (4 bytes per cell). */
    private static final long MAX_CELLS = 60_000_000L;

    private WordTimer() {}

    /** Splits like the web renderer does: on whitespace, dropping empty pieces. */
    static String[] spans(String cyr) {
        String trimmed = cyr == null ? "" : cyr.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    /**
     * @param cyr paragraph text as shown in the Cyrillic column
     * @param heard the transcript words of the cue, in order
     * @param cueStart start time of the cue
     * @param cueEnd end time of the cue
     * @return one start time per span of {@code cyr}, never decreasing, within [cueStart, cueEnd]
     */
    static double[] wordStarts(String cyr, List<CueWord> heard, double cueStart, double cueEnd) {
        String[] spans = spans(cyr);
        int n = spans.length;
        double[] starts = new double[n];
        if (n == 0) {
            return starts;
        }

        String[] book = new String[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            book[i] = fold(spans[i]);
            weight[i] = book[i].length();
        }
        int m = heard.size();
        String[] spoken = new String[m];
        for (int j = 0; j < m; j++) {
            spoken[j] = fold(heard.get(j).text);
        }

        int[] anchor = align(book, spoken); // anchor[i] = index into heard, or -1

        // Position of each span on a "reading" axis: letters of all spans in front of it.
        double[] axis = new double[n + 1];
        for (int i = 0; i < n; i++) {
            axis[i + 1] = axis[i] + weight[i];
        }

        // Times of the aligned spans, kept non-decreasing and inside the cue.
        double[] anchorTime = new double[n];
        double last = cueStart;
        for (int i = 0; i < n; i++) {
            if (anchor[i] >= 0) {
                last = Math.min(cueEnd, Math.max(last, heard.get(anchor[i]).start));
                anchorTime[i] = last;
            }
        }

        // Spans without a heard word are placed between the nearest aligned neighbours (or the
        // cue start / end), in proportion to the letters in between.
        double[] nextTime = new double[n + 1];
        double[] nextAxis = new double[n + 1];
        nextTime[n] = cueEnd;
        nextAxis[n] = axis[n];
        for (int i = n - 1; i >= 0; i--) {
            if (anchor[i] >= 0) {
                nextTime[i] = anchorTime[i];
                nextAxis[i] = axis[i];
            } else {
                nextTime[i] = nextTime[i + 1];
                nextAxis[i] = nextAxis[i + 1];
            }
        }
        double prevAxis = 0;
        double prevTime = cueStart;
        for (int i = 0; i < n; i++) {
            if (anchor[i] >= 0) {
                starts[i] = anchorTime[i];
                prevAxis = axis[i];
                prevTime = anchorTime[i];
            } else {
                starts[i] = interpolate(axis[i], prevAxis, prevTime, nextAxis[i], nextTime[i]);
            }
        }
        return starts;
    }

    private static double interpolate(double x, double xa, double ta, double xb, double tb) {
        if (xb <= xa) {
            return ta;
        }
        double f = (x - xa) / (xb - xa);
        return ta + f * (tb - ta);
    }

    /**
     * Order-preserving alignment (weighted longest common subsequence).
     *
     * @return for every book word the index of the transcript word it is paired with, or -1
     */
    static int[] align(String[] book, String[] spoken) {
        int n = book.length;
        int m = spoken.length;
        if ((long) n * m > MAX_CELLS) {
            throw new AlignException("Segment too long to align: " + n + " book words x " + m
                    + " transcript words. Split it into smaller segments.");
        }
        int[][] score = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                int best = Math.max(score[i + 1][j], score[i][j + 1]);
                int pair = pairScore(book[i], spoken[j]);
                if (pair > 0) {
                    best = Math.max(best, pair + score[i + 1][j + 1]);
                }
                score[i][j] = best;
            }
        }
        int[] anchor = new int[n];
        java.util.Arrays.fill(anchor, -1);
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            int pair = pairScore(book[i], spoken[j]);
            if (pair > 0 && score[i][j] == pair + score[i + 1][j + 1]) {
                anchor[i] = j;
                i++;
                j++;
            } else if (score[i + 1][j] >= score[i][j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return anchor;
    }

    private static int pairScore(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return EXACT;
        }
        int longest = Math.max(a.length(), b.length());
        if (Math.min(a.length(), b.length()) < 4) {
            return 0;
        }
        int allowed = Math.min(3, (longest + 3) / 4); // 4-5 letters: 1, 6-9: 2, 10+: 3
        return levenshtein(a, b, allowed) <= allowed ? FUZZY : 0;
    }

    /** Lowercase, letters only, with the spelling variants of {@code ё} folded to {@code е}. */
    static String fold(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return s.replaceAll("[^\\p{L}]", "");
    }

    /** Levenshtein distance, giving up (returning limit + 1) once it must exceed {@code limit}. */
    private static int levenshtein(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin > limit) {
                return limit + 1;
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
