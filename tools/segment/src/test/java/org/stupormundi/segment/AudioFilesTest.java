package org.stupormundi.segment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests for {@link AudioFiles}. */
class AudioFilesTest {

    @TempDir
    Path audio;

    @Test
    void returnsOnlyMp3FilesSortedByName() throws IOException {
        Files.writeString(audio.resolve("02_b.MP3"), "");
        Files.writeString(audio.resolve("01_a.mp3"), "");
        Files.writeString(audio.resolve("notes.txt"), "");
        Files.createDirectories(audio.resolve("nested.mp3"));

        List<String> names = AudioFiles.list(audio).stream()
                .map(p -> p.getFileName().toString())
                .toList();

        assertEquals(List.of("01_a.mp3", "02_b.MP3"), names);
    }

    @Test
    void returnsAnEmptyListForAnEmptyDirectory() throws IOException {
        assertEquals(List.of(), AudioFiles.list(audio));
    }
}
