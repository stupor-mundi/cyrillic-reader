package org.stupormundi.textprep;

/** A source text language; the code is used in the JSON {@code lang} field and in paragraph IDs. */
enum Language {
    RU("ru"),
    EN("en");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }

    boolean transliterates() {
        return this == RU;
    }
}
