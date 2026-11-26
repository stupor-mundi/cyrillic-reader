package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An English paragraph of {@code en.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnParagraph {
    public String id;
    public String text;
}
