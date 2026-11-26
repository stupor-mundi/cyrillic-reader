package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Tests for reading a Messages API response (no network involved). */
class AnthropicClientTest {

    @Test
    void returnsTheTextOfTheFirstContentBlock() throws IOException {
        String body = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"},{\"type\":\"text\",\"text\":\"ignored\"}]}";
        assertEquals("hello", AnthropicClient.extractText(body));
    }

    @Test
    void failsWhenThereIsNoContentArray() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AnthropicClient.extractText("{\"id\":\"x\"}"));
        assertEquals("Anthropic response missing 'content' array.", e.getMessage());
    }

    @Test
    void failsWhenTheContentArrayIsEmpty() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AnthropicClient.extractText("{\"content\":[]}"));
        assertEquals("Anthropic response missing 'content' array.", e.getMessage());
    }

    @Test
    void failsWhenTheFirstBlockHasNoText() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AnthropicClient.extractText("{\"content\":[{\"type\":\"tool_use\"}]}"));
        assertEquals("Anthropic response content[0] has no 'text' field.", e.getMessage());
    }
}
