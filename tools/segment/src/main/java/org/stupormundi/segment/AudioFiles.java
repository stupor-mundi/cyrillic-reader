package org.stupormundi.segment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lists the audio files of a book. */
final class AudioFiles {

    private AudioFiles() {
    }

    /** Returns the regular {@code .mp3} files (case-insensitive) in the directory, sorted by file name. */
    static List<Path> list(Path audioDir) throws IOException {
        try (Stream<Path> stream = Files.list(audioDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".mp3"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }
}
