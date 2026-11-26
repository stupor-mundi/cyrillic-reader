package org.stupormundi.textprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end characterization tests: ru.txt / en.txt in, ru.json / en.json out. */
class MainTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dataDir;

    private JsonNode run(String ru, String en) throws IOException {
        Path book = Files.createDirectories(dataDir.resolve("book"));
        Files.writeString(book.resolve("ru.txt"), ru, StandardCharsets.UTF_8);
        Files.writeString(book.resolve("en.txt"), en, StandardCharsets.UTF_8);
        Main.main(new String[] {dataDir.toString(), "book"});
        ObjectNode result = MAPPER.createObjectNode();
        result.set("ru", MAPPER.readTree(book.resolve("ru.json").toFile()));
        result.set("en", MAPPER.readTree(book.resolve("en.json").toFile()));
        return result;
    }

    @Test
    void splitsOnEmptyLinesAndAssignsSixDigitIds() throws IOException {
        JsonNode out = run("Глава I\n\nОтец мой\n\n\n\nКонец", "Chapter I\n\nMy father\n\nEnd");

        JsonNode ru = out.get("ru");
        assertEquals("ru", ru.get("lang").asText());
        assertEquals(3, ru.get("paragraphs").size());
        assertEquals("ru-000001", ru.get("paragraphs").get(0).get("id").asText());
        assertEquals("Глава I", ru.get("paragraphs").get(0).get("cyr").asText());
        assertEquals("ru-000003", ru.get("paragraphs").get(2).get("id").asText());

        JsonNode en = out.get("en");
        assertEquals("en", en.get("lang").asText());
        assertEquals(3, en.get("paragraphs").size());
        assertEquals("en-000002", en.get("paragraphs").get(1).get("id").asText());
        assertEquals("My father", en.get("paragraphs").get(1).get("text").asText());
        assertFalse(en.get("paragraphs").get(0).has("lat"));
    }

    @Test
    void transliteratesRussianIntoLatinLetters() throws IOException {
        JsonNode first = run("Глава", "Chapter").get("ru").get("paragraphs").get(0);
        assertEquals("Glava", first.get("lat").asText());
    }

    @Test
    void stripsFootnoteMarkersAndInvisibleCharacters() throws IOException {
        String ru = "Текст[12] дальше\u200B без\uFEFF мусора";
        JsonNode first = run(ru, "x").get("ru").get("paragraphs").get(0);
        assertEquals("Текст дальше без мусора", first.get("cyr").asText());
    }

    @Test
    void normalizesUnicodeSpacesAndWindowsLineEndings() throws IOException {
        JsonNode paragraphs = run("один\u00A0два\r\n\r\nтри", "x").get("ru").get("paragraphs");
        assertEquals(2, paragraphs.size());
        assertEquals("один два", paragraphs.get(0).get("cyr").asText());
        assertEquals("три", paragraphs.get(1).get("cyr").asText());
    }

    @Test
    void splitsParagraphsLongerThanTwelveHundredCharacters() throws IOException {
        String longPara = "Это очень длинное предложение. ".repeat(100).trim();
        JsonNode paragraphs = run(longPara, "x").get("ru").get("paragraphs");
        assertTrue(paragraphs.size() > 1);
        for (JsonNode p : paragraphs) {
            assertTrue(p.get("cyr").asText().length() <= 1200);
        }
    }
}
