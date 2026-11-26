package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for {@link PromptBuilder}. The golden files were captured from the code before it
 * was split out of Main.java, so they prove the prompt is unchanged.
 */
class PromptBuilderTest {

    private static String golden(String name) throws IOException {
        try (InputStream in = PromptBuilderTest.class.getResourceAsStream(name)) {
            assertNotNull(in, "missing test resource " + name);
            // Normalize line endings in case git checked the file out with CRLF.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static RuParagraph ru(String id, String cyr) {
        RuParagraph p = new RuParagraph();
        p.id = id;
        p.cyr = cyr;
        return p;
    }

    private static EnParagraph en(String id, String text) {
        EnParagraph p = new EnParagraph();
        p.id = id;
        p.text = text;
        return p;
    }

    @Test
    void matchesTheGoldenPromptWithContextAndLineBreaksInText() throws IOException {
        String prompt = PromptBuilder.build(
                "c000121",
                List.of(ru("ru-000008", "Контекст один."), ru("ru-000009", "  Контекст\nдва.\r\n")),
                List.of(ru("ru-000010", "Первый абзац."), ru("ru-000011", "Второй абзац."), ru("ru-000012", null)),
                List.of(en("en-000007", "English one."), en("en-000008", "English\ntwo."), en("en-000009", null)));

        assertEquals(golden("prompt-full.golden.txt"), prompt);
    }

    @Test
    void matchesTheGoldenPromptWithoutContextOrEnglish() throws IOException {
        String prompt = PromptBuilder.build(
                "c000001",
                List.of(),
                List.of(ru("ru-000001", "Единственный абзац.")),
                List.of());

        assertEquals(golden("prompt-empty.golden.txt"), prompt);
    }

    @Test
    void putsEachParagraphOnOneLineWithoutLineBreaksFromTheText() {
        String prompt = PromptBuilder.build(
                "c000001",
                List.of(),
                List.of(ru("ru-000001", "line one\nline two")),
                List.of());

        assertTrue(prompt.contains("ru-000001: line one line two\n"));
    }
}
