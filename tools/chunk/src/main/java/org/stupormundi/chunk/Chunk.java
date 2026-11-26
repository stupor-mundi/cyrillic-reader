package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One display row: the Russian and English paragraphs shown together. An entry of {@code chunks.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Chunk {
    public String id;
    public List<String> ru;
    public List<String> en;
}
