package org.stupormundi.textprep;

import java.util.regex.Pattern;

/** Cleans raw book text before it is split into paragraphs. */
final class TextCleaner {

    /** Footnote markers such as {@code [12]}. */
    private static final Pattern FOOTNOTE_MARKER = Pattern.compile("\\[\\d+\\]");

    /** Invisible format characters (Unicode category Cf) and the byte order mark. */
    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\p{Cf}\\uFEFF]");

    /** Any Unicode space separator, including the non-breaking space. */
    private static final Pattern UNICODE_SPACE = Pattern.compile("\\p{Zs}");

    private TextCleaner() {
    }

    /**
     * Removes footnote markers and invisible characters, turns Unicode spaces into plain spaces and
     * normalizes line endings to {@code \n}.
     */
    static String clean(String content) {
        String result = FOOTNOTE_MARKER.matcher(content).replaceAll("");
        result = INVISIBLE_CHARS.matcher(result).replaceAll("");
        result = UNICODE_SPACE.matcher(result).replaceAll(" ");
        return result.replace("\r\n", "\n").replace("\r", "\n");
    }
}
