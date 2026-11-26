package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The contents of {@code en.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnBook {
    public String lang;
    public List<EnParagraph> paragraphs;
}
