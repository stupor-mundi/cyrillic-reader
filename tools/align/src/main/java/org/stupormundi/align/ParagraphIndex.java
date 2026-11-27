package org.stupormundi.align;

import java.util.List;
import java.util.Map;

/** The paragraphs of ru.json in book order, plus a lookup from paragraph id to list position. */
record ParagraphIndex(List<Paragraph> paragraphs, Map<String, Integer> idToIndex) {}
