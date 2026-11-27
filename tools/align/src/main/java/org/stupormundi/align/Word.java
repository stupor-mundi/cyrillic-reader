package org.stupormundi.align;

public class Word {
    public String word;
    public double start;
    public double end;
    /** Normalised form of {@link #word}, set by the loader. Not part of the input JSON. */
    public String norm;
}
