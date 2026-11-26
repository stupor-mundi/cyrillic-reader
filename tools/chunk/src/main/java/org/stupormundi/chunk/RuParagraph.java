package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A Russian paragraph of {@code ru.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuParagraph {
    public String id;
    public String cyr;
    public String lat;
}
