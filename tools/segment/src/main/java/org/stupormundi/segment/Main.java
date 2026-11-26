package org.stupormundi.segment;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Command line entry point: pairs chapters of {@code ru.json} with MP3 files and writes {@code audio_index.json}. */
public class Main {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar segment.jar <dataDir> <bookId>");
            System.exit(1);
        }

        String bookId = args[1];
        Path baseDir = Paths.get(args[0], bookId);
        Path ruJsonPath = baseDir.resolve("ru.json");
        Path audioDir = baseDir.resolve("audio");
        Path outPath = baseDir.resolve("audio_index.json");

        try {
            AudioIndexGenerator.generate(bookId, ruJsonPath, audioDir, outPath);
        } catch (NoSuchFileException e) {
            System.err.println("File not found: " + e.getFile());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
    }
}
