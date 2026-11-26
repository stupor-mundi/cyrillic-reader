package org.stupormundi.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builders for test input. */
final class TestData {

    private TestData() {
    }

    static String ruId(int n) {
        return String.format("ru-%06d", n);
    }

    static String enId(int n) {
        return String.format("en-%06d", n);
    }

    static RuParagraph ru(int n) {
        RuParagraph p = new RuParagraph();
        p.id = ruId(n);
        p.cyr = "Русский абзац " + n;
        return p;
    }

    static EnParagraph en(int n) {
        EnParagraph p = new EnParagraph();
        p.id = enId(n);
        p.text = "English paragraph " + n;
        return p;
    }

    /** Russian paragraphs numbered from..to, inclusive. */
    static List<RuParagraph> ruParagraphs(int from, int to) {
        List<RuParagraph> list = new ArrayList<>();
        for (int n = from; n <= to; n++) {
            list.add(ru(n));
        }
        return list;
    }

    /** English paragraphs numbered from..to, inclusive. */
    static List<EnParagraph> enParagraphs(int from, int to) {
        List<EnParagraph> list = new ArrayList<>();
        for (int n = from; n <= to; n++) {
            list.add(en(n));
        }
        return list;
    }

    static RuBook ruBook(int count) {
        RuBook book = new RuBook();
        book.lang = "ru";
        book.paragraphs = ruParagraphs(1, count);
        return book;
    }

    static EnBook enBook(int count) {
        EnBook book = new EnBook();
        book.lang = "en";
        book.paragraphs = enParagraphs(1, count);
        return book;
    }

    /** Maps each English ID to its position in the list (as the aligner does for the whole book). */
    static Map<String, Integer> enIndex(List<EnParagraph> paragraphs) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            index.put(paragraphs.get(i).id, i);
        }
        return index;
    }

    static Chunk chunk(String id, List<String> ru, List<String> en) {
        Chunk chunk = new Chunk();
        chunk.id = id;
        chunk.ru = ru;
        chunk.en = en;
        return chunk;
    }

    static ChunkWindowResponse response(String lastRuCovered, String lastEnUsed, Chunk... chunks) {
        ChunkWindowResponse response = new ChunkWindowResponse();
        response.chunks = List.of(chunks);
        response.lastRuCovered = lastRuCovered;
        response.lastEnUsed = lastEnUsed;
        return response;
    }

    /**
     * One chunk per Russian paragraph ruFrom..ruTo, numbered from {@code firstChunkNumeric}. When
     * {@code withEn} is set, each chunk also has the English paragraph with the same number.
     */
    static Chunk[] oneToOneChunks(int ruFrom, int ruTo, int firstChunkNumeric, boolean withEn) {
        List<Chunk> chunks = new ArrayList<>();
        int chunkNumeric = firstChunkNumeric;
        for (int n = ruFrom; n <= ruTo; n++) {
            chunks.add(chunk(
                    ChunkIds.format(chunkNumeric++),
                    List.of(ruId(n)),
                    withEn ? List.of(enId(n)) : List.of()));
        }
        return chunks.toArray(new Chunk[0]);
    }
}
