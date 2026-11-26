package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link ResponseParser}. */
class ResponseParserTest {

    private static final String JSON = """
            {"chunks":[{"id":"c000001","ru":["ru-000001"],"en":["en-000001"]}],
             "lastEnUsed":"en-000001","lastRuCovered":"ru-000001"}""";

    @Test
    void parsesPlainJson() {
        ChunkWindowResponse r = ResponseParser.parse(JSON);
        assertEquals(1, r.chunks.size());
        assertEquals("c000001", r.chunks.get(0).id);
        assertEquals(List.of("ru-000001"), r.chunks.get(0).ru);
        assertEquals("en-000001", r.lastEnUsed);
        assertEquals("ru-000001", r.lastRuCovered);
    }

    @Test
    void ignoresACodeFenceAndTextAroundTheJson() {
        String reply = "Here you go:\n```json\n" + JSON + "\n```\nHope that helps!";
        assertEquals("ru-000001", ResponseParser.parse(reply).lastRuCovered);
    }

    @Test
    void ignoresUnknownProperties() {
        String reply = "{\"chunks\":[],\"lastEnUsed\":\"\",\"lastRuCovered\":\"ru-000001\",\"note\":\"x\"}";
        assertEquals("ru-000001", ResponseParser.parse(reply).lastRuCovered);
    }

    @Test
    void extractsFromTheFirstOpeningBraceToTheLastClosingBrace() {
        assertEquals("{\"a\":{\"b\":1}}", ResponseParser.extractJsonPayload("x {\"a\":{\"b\":1}} y"));
    }

    @Test
    void returnsTheTrimmedTextWhenThereAreNoBraces() {
        assertEquals("not json", ResponseParser.extractJsonPayload("  not json \n"));
    }

    @Test
    void aNullReplyIsAnError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ResponseParser.parse(null));
        assertEquals("LLM returned null text content.", e.getMessage());
    }

    @Test
    void textThatIsNotJsonIsAnErrorThatShowsThePayload() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ResponseParser.parse("nope"));
        assertTrue(e.getMessage().startsWith("Failed to parse LLM JSON payload"));
        assertTrue(e.getMessage().endsWith("Payload:\nnope"));
    }
}
