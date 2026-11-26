package org.stupormundi.segment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end characterization tests: ru.json and audio files in, audio_index.json out. */
class MainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String RU_JSON = """
            {"lang":"ru","paragraphs":[
              {"id":"ru-000001","cyr":"Глава I","lat":"Glava I"},
              {"id":"ru-000002","cyr":"Первый абзац","lat":"Pervyj abzac"},
              {"id":"ru-000003","cyr":"Глава II","lat":"Glava II"},
              {"id":"ru-000004","cyr":"Второй абзац","lat":"Vtoroj abzac"}
            ]}
            """;

    @TempDir
    Path dataDir;

    private Path newBook(String... mp3Names) throws IOException {
        Path book = Files.createDirectories(dataDir.resolve("book"));
        Files.writeString(book.resolve("ru.json"), RU_JSON, StandardCharsets.UTF_8);
        Path audio = Files.createDirectories(book.resolve("audio"));
        for (String name : mp3Names) {
            Files.writeString(audio.resolve(name), "");
        }
        return book;
    }

    private JsonNode runAndRead(Path book) throws IOException {
        Main.main(new String[] {dataDir.toString(), "book"});
        return MAPPER.readTree(book.resolve("audio_index.json").toFile());
    }

    @Test
    void pairsChaptersWithSortedMp3sAndWritesTheIndex() throws IOException {
        // Created in reverse order on purpose: pairing must follow file-name order.
        JsonNode index = runAndRead(newBook("02_glava-2.mp3", "01_glava-1.mp3"));

        assertEquals("book", index.get("bookId").asText());
        JsonNode segments = index.get("segments");
        assertEquals(2, segments.size());

        JsonNode s1 = segments.get(0);
        assertEquals("s001", s1.get("id").asText());
        assertEquals("Глава I", s1.get("title").asText());
        assertEquals("audio/01_glava-1.mp3", s1.get("audioFile").asText());
        assertEquals("ru-000001", s1.get("startParagraph").asText());
        assertEquals("ru-000002", s1.get("contentStartParagraph").asText());
        assertEquals("ru-000002", s1.get("endParagraph").asText());

        JsonNode s2 = segments.get(1);
        assertEquals("s002", s2.get("id").asText());
        assertEquals("audio/02_glava-2.mp3", s2.get("audioFile").asText());
        assertEquals("ru-000003", s2.get("startParagraph").asText());
        assertEquals("ru-000004", s2.get("endParagraph").asText());
    }

    @Test
    void chaptersWithoutAnAudioFileGetNoAudioFileField() throws IOException {
        JsonNode segments = runAndRead(newBook("01_glava-1.mp3")).get("segments");

        assertEquals(2, segments.size());
        assertEquals("audio/01_glava-1.mp3", segments.get(0).get("audioFile").asText());
        assertFalse(segments.get(1).has("audioFile"));
    }
}
