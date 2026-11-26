package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The contents of {@code ru.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuBook {
    public String lang;
    public List<RuParagraph> paragraphs;
}
