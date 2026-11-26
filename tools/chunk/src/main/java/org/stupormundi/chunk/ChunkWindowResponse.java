package org.stupormundi.chunk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** The JSON object the LLM returns for one window. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkWindowResponse {
    public List<Chunk> chunks;
    public String lastEnUsed;
    public String lastRuCovered;
}
