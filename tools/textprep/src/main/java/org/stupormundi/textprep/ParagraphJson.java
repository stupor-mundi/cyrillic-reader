package org.stupormundi.textprep;

import com.ibm.icu.text.Transliterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the contents of {@code ru.json} and {@code en.json}.
 *
 * <p>Russian paragraphs have {@code id}, {@code cyr} and {@code lat}; English paragraphs have
 * {@code id} and {@code text}. IDs are {@code <lang>-NNNNNN}, counted from 1.
 */
final class ParagraphJson {

    private ParagraphJson() {
    }

    static Map<String, Object> build(Language language, List<String> paragraphs) {
        Transliterator transliterator =
                language.transliterates() ? Transliterator.getInstance("Cyrillic-Latin") : null;

        List<Map<String, String>> entries = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String id = String.format("%s-%06d", language.code(), i + 1);
            String text = paragraphs.get(i);

            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", id);
            if (transliterator != null) {
                entry.put("cyr", text);
                entry.put("lat", transliterator.transliterate(text));
            } else {
                entry.put("text", text);
            }
            entries.add(entry);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("lang", language.code());
        output.put("paragraphs", entries);
        return output;
    }
}
