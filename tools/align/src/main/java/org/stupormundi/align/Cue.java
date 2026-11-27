package org.stupormundi.align;

import java.util.List;

public class Cue {
    public String paragraphId;
    public double start;
    public double end;
    public int wordOffset;
    public List<CueWord> words;
    /** Start time of each whitespace-separated word of the paragraph's Cyrillic text, in order. */
    public double[] wordStarts;
}
