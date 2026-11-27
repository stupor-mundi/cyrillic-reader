package org.stupormundi.align;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Characterization tests that run {@link Main#run} on tiny made-up books in a temp directory. They
 * lock how the matcher results are turned into cues. Error paths that call System.exit are not covered.
 */
class MainEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dataDir;

    /** Writes ru.json, audio_index.json and words/s001.json for book "b" and runs the aligner. */
    private JsonNode align(String[] paragraphs, String startId, String contentStartId, String endId,
                           double[][] wordTimes, String... wordTexts) throws IOException {
        Path book = dataDir.resolve("b");
        Files.createDirectories(book.resolve("words"));

        StringBuilder ru = new StringBuilder("{\"lang\":\"ru\",\"paragraphs\":[");
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) ru.append(',');
            ru.append(String.format("{\"id\":\"ru-%06d\",\"cyr\":%s,\"lat\":\"\"}",
                    i + 1, MAPPER.writeValueAsString(paragraphs[i])));
        }
        ru.append("]}");
        write(book.resolve("ru.json"), ru.toString());

        write(book.resolve("audio_index.json"), String.format(
                "{\"bookId\":\"b\",\"segments\":[{\"id\":\"s001\",\"title\":\"T\","
                        + "\"audioFile\":\"audio/01.mp3\",\"startParagraph\":\"%s\","
                        + "\"contentStartParagraph\":\"%s\",\"endParagraph\":\"%s\"}]}",
                startId, contentStartId, endId));

        List<String> words = new ArrayList<>();
        for (int i = 0; i < wordTexts.length; i++) {
            words.add(String.format(java.util.Locale.ROOT, "{\"word\":%s,\"start\":%s,\"end\":%s}",
                    MAPPER.writeValueAsString(wordTexts[i]), wordTimes[i][0], wordTimes[i][1]));
        }
        write(book.resolve("words").resolve("s001.json"), "[" + String.join(",", words) + "]");

        Main.run(dataDir.toString(), "b", "s001");

        return MAPPER.readTree(book.resolve("cues").resolve("s001.json").toFile());
    }

    /** Word times of 1 second per word: word i spans [i, i + 0.5]. */
    private static double[][] evenTimes(int n) {
        double[][] t = new double[n][2];
        for (int i = 0; i < n; i++) {
            t[i][0] = i;
            t[i][1] = i + 0.5;
        }
        return t;
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static List<String> cueIds(JsonNode result) {
        List<String> ids = new ArrayList<>();
        for (JsonNode cue : result.get("cues")) ids.add(cue.get("paragraphId").asText());
        return ids;
    }

    @Test
    void buildsCuesWithTimingsFromFirstAndLastMatchedWord() throws IOException {
        String[] words = {"александр", "сергеевич", "пушкин", "глава", "первая",
                "мой", "отец", "служил", "при", "графе",
                "он", "вышел", "в", "отставку"};
        JsonNode result = align(
                new String[] {"Глава первая", "Мой отец служил при графе.", "Он вышел в отставку."},
                "ru-000001", "ru-000002", "ru-000003", evenTimes(words.length), words);

        assertEquals("b", result.get("bookId").asText());
        assertEquals("audio/01.mp3", result.get("audioFile").asText());
        assertEquals(List.of("ru-000002", "ru-000003"), cueIds(result));

        JsonNode first = result.get("cues").get(0);
        assertEquals(5.0, first.get("start").asDouble());
        assertEquals(9.5, first.get("end").asDouble());
        assertEquals(0, first.get("wordOffset").asInt());
        assertEquals(5, first.get("words").size());
        assertEquals("мой", first.get("words").get(0).get("text").asText());
        assertEquals(5.0, first.get("words").get(0).get("start").asDouble());
        assertEquals(5.5, first.get("words").get(0).get("end").asDouble());

        JsonNode second = result.get("cues").get(1);
        assertEquals(10.0, second.get("start").asDouble());
        assertEquals(13.5, second.get("end").asDouble());
        assertEquals(4, second.get("words").size());
    }

    @Test
    void anchorParagraphSkipsTheSpokenPreamble() throws IOException {
        // "мой отец" is also spoken in the preamble; the anchor (heading) paragraph moves the search past it.
        String[] words = {"мой", "отец", "глава", "первая", "мой", "отец", "служил"};
        JsonNode result = align(
                new String[] {"Глава первая", "Мой отец служил."},
                "ru-000001", "ru-000002", "ru-000002", evenTimes(words.length), words);

        assertEquals(1, result.get("cues").size());
        assertEquals(4.0, result.get("cues").get(0).get("start").asDouble());
    }

    @Test
    void anchorThatIsNotFoundFallsBackToSearchingFromTheStart() throws IOException {
        String[] words = {"мой", "отец", "служил"};
        JsonNode result = align(
                new String[] {"Совершенно другой заголовок", "Мой отец служил."},
                "ru-000001", "ru-000002", "ru-000002", evenTimes(words.length), words);

        assertEquals(List.of("ru-000002"), cueIds(result));
        assertEquals(0.0, result.get("cues").get(0).get("start").asDouble());
    }

    @Test
    void unmatchedParagraphIsOmittedAndDoesNotBlockTheNextOne() throws IOException {
        String[] words = {"глава", "мой", "отец", "служил", "при", "графе", "минихе", "потом", "вышел"};
        JsonNode result = align(
                new String[] {"Глава", "Мой отец служил.", "Совершенно иной текст про далёкое море.",
                        "При графе Минихе."},
                "ru-000001", "ru-000002", "ru-000004", evenTimes(words.length), words);

        assertEquals(List.of("ru-000002", "ru-000004"), cueIds(result));
        assertEquals(4.0, result.get("cues").get(1).get("start").asDouble());
    }

    @Test
    void paragraphsOutsideTheContentRangeGetNoCue() throws IOException {
        String[] words = {"глава", "мой", "отец", "служил", "при", "графе"};
        JsonNode result = align(
                new String[] {"Глава", "Мой отец служил.", "При графе."},
                "ru-000001", "ru-000002", "ru-000002", evenTimes(words.length), words);

        assertEquals(List.of("ru-000002"), cueIds(result));
    }

    @Test
    void aParagraphWithLongStretchesOfUnrecognisedWordsStillGetsItsCueAndDoesNotDisplaceTheNext()
            throws IOException {
        // Twelve unrecognised words in a row: the old scan gave up on such a paragraph and a later
        // spurious match could then displace the paragraphs after it. The global alignment does not.
        String bad = "щщщщщщщщщщщщщщ";
        String[] words = {"глава",
                "жили", "были", "дед", "да", "баба", "ели", bad, bad, bad, bad, bad, bad, bad, bad, bad,
                bad, bad, bad, "кашу", "пили", "чай", "потом", "пришел", "кот", "серый", "хвостом", "махнул"};
        JsonNode result = align(
                new String[] {"Глава", "Жили были дед да баба ели пирог с капустой и ещё кашу пили чай.",
                        "Потом пришел кот серый.", "Хвостом махнул."},
                "ru-000001", "ru-000002", "ru-000004", evenTimes(words.length), words);

        assertEquals(List.of("ru-000002", "ru-000003", "ru-000004"), cueIds(result));
        assertEquals(1.0, result.get("cues").get(0).get("start").asDouble());
        assertEquals(22.0, result.get("cues").get(1).get("start").asDouble());
        assertEquals(26.0, result.get("cues").get(2).get("start").asDouble());
    }

    @Test
    void cuesNeverRunBackwardsBecauseParagraphsAreAlignedInOrder() throws IOException {
        // The words of the second paragraph were spoken before those of the first in the transcript
        // (a reordered recording): the later paragraph in the book cannot claim earlier audio.
        // The first paragraph is the longer match, so it wins; the second one gets no cue.
        String[] words = {"глава", "при", "графе", "минихе", "мой", "отец", "служил", "верно"};
        JsonNode result = align(
                new String[] {"Глава", "Мой отец служил верно.", "При графе минихе."},
                "ru-000001", "ru-000002", "ru-000003", evenTimes(words.length), words);

        assertEquals(List.of("ru-000002"), cueIds(result));
    }

    @Test
    void aFarAwayStrayMatchOfOneWordDoesNotStretchTheCue() throws IOException {
        // "минихе" is also heard 30 words after the rest of the paragraph; that stray pairing is ignored.
        List<String> words = new ArrayList<>(List.of("глава", "при", "графе"));
        for (int i = 0; i < 30; i++) {
            words.add("щщщщщщщщщщщщщщ");
        }
        words.add("минихе");
        String[] wordArray = words.toArray(new String[0]);
        JsonNode result = align(
                new String[] {"Глава", "При графе минихе."},
                "ru-000001", "ru-000002", "ru-000002", evenTimes(wordArray.length), wordArray);

        JsonNode cue = result.get("cues").get(0);
        assertEquals(1.0, cue.get("start").asDouble());
        assertEquals(2.5, cue.get("end").asDouble());
        assertEquals(2, cue.get("words").size());
    }

    @Test
    void wordOffsetIsWrittenToTheCue() throws IOException {
        // Three unrelated transcript words stand in for the first paragraph token.
        String bad = "щщщщщщщщщщщщщщ";
        String[] words = {"глава", bad, bad, bad, "размышления", "я", "ехал"};
        JsonNode result = align(
                new String[] {"Глава", "Дорожные размышления я ехал."},
                "ru-000001", "ru-000002", "ru-000002", evenTimes(words.length), words);

        assertEquals(1, result.get("cues").size());
        assertEquals(1, result.get("cues").get(0).get("wordOffset").asInt());
        assertEquals(4.0, result.get("cues").get(0).get("start").asDouble());
    }

    @Test
    void cyrillicTextIsWrittenUnescaped() throws IOException {
        String[] words = {"глава", "мой", "отец"};
        align(new String[] {"Глава", "Мой отец."}, "ru-000001", "ru-000002", "ru-000002",
                evenTimes(words.length), words);

        String raw = Files.readString(dataDir.resolve("b/cues/s001.json"), StandardCharsets.UTF_8);
        assertTrue(raw.contains("\"text\" : \"мой\""));
        assertFalse(raw.contains("\\u"));
    }
}
