package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Characterization tests for how {@link Main#run} reports bad input. {@link Main#main} turns an
 * {@link AlignException} into "message, exit code 1" and an {@link IOException} into "I/O error: message".
 */
class MainErrorTest {

    @TempDir
    Path dataDir;

    private Path book() throws IOException {
        Path book = dataDir.resolve("b");
        Files.createDirectories(book.resolve("words"));
        return book;
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** A valid book: heading, one content paragraph, and words that match it. */
    private Path validBook(String startId, String contentStartId, String endId) throws IOException {
        Path book = book();
        write(book.resolve("ru.json"), "{\"paragraphs\":[{\"id\":\"ru-000001\",\"cyr\":\"Глава\"},"
                + "{\"id\":\"ru-000002\",\"cyr\":\"Мой отец служил.\"}]}");
        write(book.resolve("audio_index.json"), "{\"segments\":[{\"id\":\"s001\",\"audioFile\":\"a.mp3\","
                + "\"startParagraph\":\"" + startId + "\",\"contentStartParagraph\":\"" + contentStartId + "\","
                + "\"endParagraph\":\"" + endId + "\"}]}");
        write(book.resolve("words/s001.json"), "[{\"word\":\"глава\",\"start\":0,\"end\":1},"
                + "{\"word\":\"мой\",\"start\":1,\"end\":2},{\"word\":\"отец\",\"start\":2,\"end\":3},"
                + "{\"word\":\"служил\",\"start\":3,\"end\":4}]");
        return book;
    }

    private AlignException alignError() {
        return assertThrows(AlignException.class, () -> Main.run(dataDir.toString(), "b", "s001"));
    }

    @Test
    void missingWordsFileIsReported() throws IOException {
        Path book = validBook("ru-000001", "ru-000002", "ru-000002");
        Files.delete(book.resolve("words/s001.json"));
        assertEquals("Words file not found: " + book.resolve("words/s001.json").toAbsolutePath(), alignError().getMessage());
    }

    @Test
    void missingRuJsonIsReported() throws IOException {
        Path book = validBook("ru-000001", "ru-000002", "ru-000002");
        Files.delete(book.resolve("ru.json"));
        assertEquals("ru.json not found: " + book.resolve("ru.json").toAbsolutePath(), alignError().getMessage());
    }

    @Test
    void missingAudioIndexIsReported() throws IOException {
        Path book = validBook("ru-000001", "ru-000002", "ru-000002");
        Files.delete(book.resolve("audio_index.json"));
        assertEquals("audio_index.json not found: " + book.resolve("audio_index.json").toAbsolutePath(),
                alignError().getMessage());
    }

    @Test
    void unknownSegmentIsReported() throws IOException {
        validBook("ru-000001", "ru-000002", "ru-000002");
        write(dataDir.resolve("b/words/s009.json"), "[]");
        AlignException e = assertThrows(AlignException.class, () -> Main.run(dataDir.toString(), "b", "s009"));
        assertEquals("Segment not found in audio_index.json for segmentId: s009", e.getMessage());
    }

    @Test
    void unknownStartParagraphIsReported() throws IOException {
        validBook("ru-999999", "ru-000002", "ru-000002");
        assertEquals("Start paragraph ID not found in ru.json: ru-999999", alignError().getMessage());
    }

    @Test
    void unknownContentStartParagraphIsReported() throws IOException {
        validBook("ru-000001", "ru-999999", "ru-000002");
        assertEquals("contentStartParagraph ID not found in ru.json: ru-999999", alignError().getMessage());
    }

    @Test
    void unknownEndParagraphIsReported() throws IOException {
        validBook("ru-000001", "ru-000002", "ru-999999");
        assertEquals("endParagraph ID not found in ru.json: ru-999999", alignError().getMessage());
    }

    @Test
    void contentStartAfterEndIsReported() throws IOException {
        validBook("ru-000001", "ru-000002", "ru-000001");
        assertEquals("Invalid paragraph range: contentStartParagraph comes after endParagraph.", alignError().getMessage());
    }

    @Test
    void invalidRuJsonIsAnIoErrorNotAnAlignException() throws IOException {
        Path book = validBook("ru-000001", "ru-000002", "ru-000002");
        write(book.resolve("ru.json"), "{\"lang\":\"ru\"}");
        IOException e = assertThrows(IOException.class, () -> Main.run(dataDir.toString(), "b", "s001"));
        assertEquals("Invalid ru.json: expected 'paragraphs' array or top-level array.", e.getMessage());
    }

    @Test
    void noCuesFileIsWrittenWhenInputIsRejected() throws IOException {
        Path book = validBook("ru-999999", "ru-000002", "ru-000002");
        alignError();
        assertFalse(Files.exists(book.resolve("cues/s001.json")));
    }

    @Test
    void emptyWordsFileGivesAnEmptyCuesFile() throws IOException {
        Path book = validBook("ru-000001", "ru-000002", "ru-000002");
        write(book.resolve("words/s001.json"), "[]");
        Main.run(dataDir.toString(), "b", "s001");
        String json = Files.readString(book.resolve("cues/s001.json"), StandardCharsets.UTF_8);
        assertEquals(true, json.contains("\"cues\" : [ ]"));
    }
}
