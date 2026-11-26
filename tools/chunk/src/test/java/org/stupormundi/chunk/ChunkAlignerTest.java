package org.stupormundi.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.stupormundi.chunk.TestData.chunk;
import static org.stupormundi.chunk.TestData.enId;
import static org.stupormundi.chunk.TestData.oneToOneChunks;
import static org.stupormundi.chunk.TestData.response;
import static org.stupormundi.chunk.TestData.ruId;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests for {@link ChunkAligner}, using a fake model instead of the Anthropic API. */
class ChunkAlignerTest {

    /** Replies with the queued answers in order and records every prompt it receives. */
    private static final class FakeLlm implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();

        FakeLlm reply(ChunkWindowResponse response) throws IOException {
            replies.add(Json.MAPPER.writeValueAsString(response));
            return this;
        }

        FakeLlm replyRaw(String text) {
            replies.add(text);
            return this;
        }

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            String next = replies.poll();
            if (next == null) {
                throw new AssertionError("the aligner asked for more replies than the test provided");
            }
            return next;
        }
    }

    /** A valid answer for a book of three Russian and three English paragraphs. */
    private static ChunkWindowResponse validForThreeParagraphs() {
        return response(ruId(3), enId(3),
                chunk("c000001", List.of(ruId(1), ruId(2)), List.of(enId(1), enId(2))),
                chunk("c000002", List.of(ruId(3)), List.of(enId(3))));
    }

    /** An answer that fails validation: lastEnUsed does not match the English paragraphs used. */
    private static ChunkWindowResponse invalidForThreeParagraphs() {
        ChunkWindowResponse r = validForThreeParagraphs();
        r.lastEnUsed = enId(1);
        return r;
    }

    @Test
    void aSingleWindowBookIsAlignedWithOneCall() throws Exception {
        FakeLlm llm = new FakeLlm().reply(validForThreeParagraphs());

        List<Chunk> chunks = new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3));

        assertEquals(1, llm.prompts.size());
        assertEquals(List.of("c000001", "c000002"), chunks.stream().map(c -> c.id).toList());
        assertEquals(List.of(ruId(1), ruId(2)), chunks.get(0).ru);
        assertEquals(List.of(enId(3)), chunks.get(1).en);
    }

    @Test
    void theFirstPromptStartsAtChunkOneWithoutContext() throws Exception {
        FakeLlm llm = new FakeLlm().reply(validForThreeParagraphs());

        new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3));

        String prompt = llm.prompts.get(0);
        assertTrue(prompt.contains("Starting chunk ID:\nc000001\n"));
        assertTrue(prompt.contains("Russian CONTEXT ONLY:\n(none)\n"));
        assertTrue(prompt.contains("ru-000003: "));
    }

    @Test
    void aLongBookIsAlignedInWindowsThatContinueWhereTheLastOneStopped() throws Exception {
        // 70 Russian paragraphs: window 1 covers 1..60 (of 65 offered), window 2 covers the remaining 61..70.
        FakeLlm llm = new FakeLlm()
                .reply(response(ruId(60), enId(60), oneToOneChunks(1, 60, 1, true)))
                .reply(response(ruId(70), enId(70), oneToOneChunks(61, 70, 61, true)));

        List<Chunk> chunks = new ChunkAligner(llm).align(TestData.ruBook(70), TestData.enBook(70));

        assertEquals(2, llm.prompts.size());
        assertEquals(70, chunks.size());
        assertEquals("c000001", chunks.get(0).id);
        assertEquals("c000070", chunks.get(69).id);

        String second = llm.prompts.get(1);
        // Chunk numbering continues.
        assertTrue(second.contains("Starting chunk ID:\nc000061\n"));
        // The 10 Russian paragraphs before the window are context only.
        String context = second.substring(
                second.indexOf("Russian CONTEXT ONLY:"), second.indexOf("Russian CANDIDATES (target"));
        assertTrue(context.contains("ru-000051: "));
        assertTrue(context.contains("ru-000060: "));
        assertFalse(context.contains("ru-000050: "));
        // The candidates start right after the covered paragraphs.
        String candidates = second.substring(
                second.indexOf("Russian CANDIDATES (target"), second.indexOf("English WINDOW INCLUDING"));
        assertTrue(candidates.contains("ru-000061: "));
        assertFalse(candidates.contains("ru-000060: "));
        // The English window starts 15 paragraphs before the paragraph after lastEnUsed (en-000060).
        String english = second.substring(second.indexOf("English WINDOW INCLUDING"));
        assertTrue(english.contains("en-000046: "));
        assertFalse(english.contains("en-000045: "));
    }

    @Test
    void theFirstWindowOffersAtMostSixtyFiveRussianParagraphs() throws Exception {
        FakeLlm llm = new FakeLlm()
                .reply(response(ruId(60), "", oneToOneChunks(1, 60, 1, false)))
                .reply(response(ruId(70), "", oneToOneChunks(61, 70, 61, false)));

        new ChunkAligner(llm).align(TestData.ruBook(70), TestData.enBook(1));

        String first = llm.prompts.get(0);
        assertTrue(first.contains("ru-000065: "));
        assertFalse(first.contains("ru-000066: "));
    }

    @Test
    void anInvalidAnswerIsRequestedAgainWithAStricterMessage() throws Exception {
        FakeLlm llm = new FakeLlm()
                .reply(invalidForThreeParagraphs())
                .reply(validForThreeParagraphs());

        List<Chunk> chunks = new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3));

        assertEquals(2, llm.prompts.size());
        assertEquals(2, chunks.size());
        String first = llm.prompts.get(0);
        String second = llm.prompts.get(1);
        assertFalse(first.contains("Your previous output was invalid."));
        assertTrue(second.startsWith(first));
        assertTrue(second.endsWith("Your previous output was invalid. Output JSON only and obey all constraints.\n"));
    }

    @Test
    void theFourthAttemptUsesTheMostUrgentMessage() throws Exception {
        FakeLlm llm = new FakeLlm()
                .reply(invalidForThreeParagraphs())
                .reply(invalidForThreeParagraphs())
                .reply(invalidForThreeParagraphs())
                .reply(validForThreeParagraphs());

        new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3));

        assertEquals(4, llm.prompts.size());
        assertFalse(llm.prompts.get(1).contains("INVALID OUTPUT."));
        assertTrue(llm.prompts.get(2).contains("INVALID OUTPUT."));
        assertTrue(llm.prompts.get(3).contains("CRITICAL:"));
    }

    @Test
    void afterFourInvalidAnswersTheLastValidationErrorIsThrown() throws Exception {
        FakeLlm llm = new FakeLlm();
        for (int i = 0; i < 4; i++) {
            llm.reply(invalidForThreeParagraphs());
        }

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3)));

        assertEquals(4, llm.prompts.size());
        assertTrue(e.getMessage().contains("lastEnUsed mismatch"));
    }

    @Test
    void aReplyThatIsNotJsonFailsTheRunWithoutRetrying() {
        FakeLlm llm = new FakeLlm().replyRaw("Sorry, I cannot do that.");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3)));

        assertEquals(1, llm.prompts.size());
        assertTrue(e.getMessage().startsWith("Failed to parse LLM JSON payload"));
    }

    @Test
    void aReplyWrappedInACodeFenceIsAccepted() {
        String fenced = "```json\n" + toJson(validForThreeParagraphs()) + "\n```";
        FakeLlm llm = new FakeLlm().replyRaw(fenced);

        assertDoesNotThrowAlign(llm);
        assertEquals(1, llm.prompts.size());
    }

    @Test
    void emptyBooksAreRejectedBeforeAnyCall() {
        FakeLlm llm = new FakeLlm();

        RuBook emptyRu = TestData.ruBook(0);
        IllegalStateException ruError = assertThrows(IllegalStateException.class,
                () -> new ChunkAligner(llm).align(emptyRu, TestData.enBook(3)));
        assertEquals("ru.json has no paragraphs.", ruError.getMessage());

        EnBook emptyEn = TestData.enBook(0);
        IllegalStateException enError = assertThrows(IllegalStateException.class,
                () -> new ChunkAligner(llm).align(TestData.ruBook(3), emptyEn));
        assertEquals("en.json has no paragraphs.", enError.getMessage());

        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void duplicateParagraphIdsAreRejectedBeforeAnyCall() {
        FakeLlm llm = new FakeLlm();
        RuBook duplicated = TestData.ruBook(3);
        duplicated.paragraphs.get(2).id = ruId(1);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ChunkAligner(llm).align(duplicated, TestData.enBook(3)));

        assertEquals("Duplicate ru id detected: ru-000001", e.getMessage());
        assertTrue(llm.prompts.isEmpty());
    }

    private static String toJson(ChunkWindowResponse response) {
        try {
            return Json.MAPPER.writeValueAsString(response);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertDoesNotThrowAlign(FakeLlm llm) {
        try {
            new ChunkAligner(llm).align(TestData.ruBook(3), TestData.enBook(3));
        } catch (Exception e) {
            throw new AssertionError("align should have succeeded", e);
        }
    }
}
