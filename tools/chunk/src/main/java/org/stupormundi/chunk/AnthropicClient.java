package org.stupormundi.chunk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** {@link LlmClient} backed by the Anthropic Messages API. Retries with backoff on HTTP 429. */
final class AnthropicClient implements LlmClient {

    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 4000;
    private static final String URL = "https://api.anthropic.com/v1/messages";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final String apiKey;

    AnthropicClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String complete(String prompt) throws IOException, InterruptedException {
        ObjectNode root = Json.MAPPER.createObjectNode();
        root.put("model", MODEL);
        root.put("max_tokens", MAX_TOKENS);

        ArrayNode messages = root.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        String body = Json.MAPPER.writeValueAsString(root);

        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofMinutes(2))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = sendWith429Retry(request);

        if (response.statusCode() != 200) {
            throw new IllegalStateException("Anthropic API returned status " + response.statusCode() + ": " + response.body());
        }
        return extractText(response.body());
    }

    /** Returns {@code content[0].text} of a Messages API response body. */
    static String extractText(String responseBody) throws IOException {
        JsonNode rootNode = Json.MAPPER.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        if (contentNode == null || !contentNode.isArray() || contentNode.isEmpty()) {
            throw new IllegalStateException("Anthropic response missing 'content' array.");
        }
        JsonNode first = contentNode.get(0);
        if (first == null) {
            throw new IllegalStateException("Anthropic response content[0] is null.");
        }
        if (!first.hasNonNull("text")) {
            throw new IllegalStateException("Anthropic response content[0] has no 'text' field.");
        }
        return first.get("text").asText();
    }

    private static HttpResponse<String> sendWith429Retry(HttpRequest request) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            HttpResponse<String> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 429) {
                return resp;
            }
            attempt++;
            // Exponential backoff: 15s, 30s, 60s, then cap at 60s
            long sleepMs = Math.min(60_000L, 15_000L * (1L << Math.min(attempt - 1, 2)));
            System.err.println("Rate limited (429). Sleeping " + (sleepMs / 1000) + "s then retrying...");
            Thread.sleep(sleepMs);
        }
    }
}
