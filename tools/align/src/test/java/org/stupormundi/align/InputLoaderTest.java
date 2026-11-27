package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests for reading the three input files. */
class InputLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dir;

    private Path file(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void loadWordsKeepsOrderAndSetsNormalisedForm() throws IOException {
        Path p = file("w.json", "[{\"word\":\"Мой\",\"start\":1.0,\"end\":1.5},{\"word\":\"отец,\",\"start\":1.5,\"end\":2.0}]");
        List<Word> words = InputLoader.loadWords(MAPPER, p);
        assertEquals(2, words.size());
        assertEquals("Мой", words.get(0).word);
        assertEquals("мой", words.get(0).norm);
        assertEquals("отец", words.get(1).norm);
        assertEquals(1.5, words.get(1).start);
    }

    @Test
    void loadWordsDropsWordsThatNormaliseToNothing() throws IOException {
        Path p = file("w.json", "[{\"word\":\"—\",\"start\":0,\"end\":1},{\"word\":\"...\",\"start\":1,\"end\":2},"
                + "{\"word\":\"1773\",\"start\":2,\"end\":3},{\"word\":\"да\",\"start\":3,\"end\":4}]");
        List<Word> words = InputLoader.loadWords(MAPPER, p);
        assertEquals(1, words.size());
        assertEquals("да", words.get(0).word);
    }

    @Test
    void loadWordsDropsEntriesWithoutWordText() throws IOException {
        Path p = file("w.json", "[null,{\"start\":0,\"end\":1},{\"word\":\"да\",\"start\":1,\"end\":2}]");
        assertEquals(1, InputLoader.loadWords(MAPPER, p).size());
    }

    @Test
    void loadParagraphsReadsParagraphsObject() throws IOException {
        Path p = file("ru.json", "{\"lang\":\"ru\",\"paragraphs\":[{\"id\":\"ru-000001\",\"cyr\":\"Да.\",\"lat\":\"Da.\"},"
                + "{\"id\":\"ru-000002\",\"cyr\":\"Нет.\"}]}");
        ParagraphIndex index = InputLoader.loadParagraphs(MAPPER, p);
        assertEquals(2, index.paragraphs().size());
        assertEquals("Да.", index.paragraphs().get(0).cyr);
        assertEquals(1, index.idToIndex().get("ru-000002"));
    }

    @Test
    void loadParagraphsAlsoAcceptsATopLevelArray() throws IOException {
        Path p = file("ru.json", "[{\"id\":\"ru-000001\",\"cyr\":\"Да.\"}]");
        assertEquals(1, InputLoader.loadParagraphs(MAPPER, p).paragraphs().size());
    }

    @Test
    void loadParagraphsSkipsEntriesWithoutIdAndTurnsMissingTextIntoEmptyString() throws IOException {
        Path p = file("ru.json", "{\"paragraphs\":[{\"cyr\":\"без id\"},{\"id\":\"ru-000002\"},{\"id\":\"ru-000003\",\"cyr\":\"Да.\"}]}");
        ParagraphIndex index = InputLoader.loadParagraphs(MAPPER, p);
        assertEquals(2, index.paragraphs().size());
        assertEquals("", index.paragraphs().get(0).cyr);
        assertEquals(0, index.idToIndex().get("ru-000002"));
        assertEquals(1, index.idToIndex().get("ru-000003"));
    }

    @Test
    void loadParagraphsRejectsOtherShapes() throws IOException {
        Path p = file("ru.json", "{\"lang\":\"ru\"}");
        IOException e = assertThrows(IOException.class, () -> InputLoader.loadParagraphs(MAPPER, p));
        assertTrue(e.getMessage().startsWith("Invalid ru.json"));
    }

    @Test
    void loadSegmentFindsTheRequestedSegment() throws IOException {
        Path p = file("ai.json", "{\"segments\":[" + segmentJson("s001") + "," + segmentJson("s002") + "]}");
        Segment s = InputLoader.loadSegment(MAPPER, p, "s002");
        assertEquals("s002", s.id);
        assertEquals("audio/s002.mp3", s.audioFile);
        assertEquals("ru-000001", s.startParagraph);
        assertEquals("ru-000002", s.contentStartParagraph);
        assertEquals("ru-000003", s.endParagraph);
    }

    @Test
    void loadSegmentAlsoAcceptsATopLevelArray() throws IOException {
        Path p = file("ai.json", "[" + segmentJson("s001") + "]");
        assertEquals("s001", InputLoader.loadSegment(MAPPER, p, "s001").id);
    }

    @Test
    void loadSegmentUnknownIdThrowsAlignException() throws IOException {
        Path p = file("ai.json", "{\"segments\":[" + segmentJson("s001") + "]}");
        AlignException e = assertThrows(AlignException.class, () -> InputLoader.loadSegment(MAPPER, p, "s009"));
        assertEquals("Segment not found in audio_index.json for segmentId: s009", e.getMessage());
    }

    @Test
    void loadSegmentWithMissingFieldsThrowsIoException() throws IOException {
        Path p = file("ai.json", "{\"segments\":[{\"id\":\"s001\",\"audioFile\":\"a.mp3\"}]}");
        IOException e = assertThrows(IOException.class, () -> InputLoader.loadSegment(MAPPER, p, "s001"));
        assertEquals("Segment s001 is missing required fields in audio_index.json.", e.getMessage());
    }

    @Test
    void loadSegmentRejectsOtherShapes() throws IOException {
        Path p = file("ai.json", "{\"bookId\":\"b\"}");
        IOException e = assertThrows(IOException.class, () -> InputLoader.loadSegment(MAPPER, p, "s001"));
        assertTrue(e.getMessage().startsWith("Invalid audio_index.json"));
    }

    @Test
    void ensureFileExistsReportsTheMissingPath() {
        Path missing = dir.resolve("nope.json");
        IOException e = assertThrows(IOException.class, () -> InputLoader.ensureFileExists(missing, "Words file not found: "));
        assertEquals("Words file not found: " + missing.toAbsolutePath(), e.getMessage());
    }

    private static String segmentJson(String id) {
        return "{\"id\":\"" + id + "\",\"title\":\"T\",\"audioFile\":\"audio/" + id + ".mp3\","
                + "\"startParagraph\":\"ru-000001\",\"contentStartParagraph\":\"ru-000002\",\"endParagraph\":\"ru-000003\"}";
    }
}
