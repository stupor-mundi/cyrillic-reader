# align

Java 21 / Maven tool that matches WhisperX word timestamps to Russian paragraphs
and writes a cues JSON file for the web reader.

## Prerequisites

Run the transcribe tool first to produce the words JSON for each segment:
`tools/transcribe/transcribe.py`

## Usage

    java -jar target/align-0.1.0.jar <dataDir> <bookId> <segmentId>

Example:

    java -jar target/align-0.1.0.jar ../../data pushkin_kd s001

## Input

    <dataDir>/<bookId>/words/<segmentId>.json     (from transcribe tool)
    <dataDir>/<bookId>/ru.json
    <dataDir>/<bookId>/audio_index.json

## Output

    <dataDir>/<bookId>/cues/<segmentId>.json

    {
      "bookId": "pushkin_kd",
      "audioFile": "audio/01_glava-1.mp3",
      "cues": [
        {
          "paragraphId": "ru-000002",
          "start": 34.402,
          "end": 47.836,
          "wordOffset": 0,
          "words": [ { "text": "Отец", "start": 34.402, "end": 34.822 } ],
          "wordStarts": [34.402, 34.822, 35.31]
        }
      ]
    }

`audioFile` is copied from `audio_index.json`. Paragraphs that are not matched
are omitted.

## How it matches

1. The segment's `startParagraph` (chapter heading) is searched for in the transcript as an anchor
   (fuzzy match, Levenshtein distance 2 or less); the spoken preamble before it is skipped.
2. All words of the paragraphs from `contentStartParagraph` to `endParagraph` are aligned to the
   transcript words after the anchor in one order-preserving pass (weighted longest common
   subsequence, `WordTimer.align`). Words are lowercased and reduced to letters, `ё` is folded to
   `е`; equal words score 2, words of 4+ letters that differ by a few letters (1 edit for 4-letter
   words, up to 2 for 5-8 letters, up to 3 for 9+) score 1, shorter words only pair when equal. Because the alignment is global, a wrong match far from where a
   paragraph belongs loses more than it gains, and cues never overlap or run backwards. (The old
   per-paragraph scan could jump far ahead on a false match and then lose every paragraph it
   skipped.)
3. A paragraph's cue runs from its first to its last paired transcript word. Pairings that lie
   more than 15 transcript words away from the rest of the paragraph are ignored (only the largest
   group of close pairings counts). A paragraph gets a cue if at least 30% of its words were paired.
4. `wordStarts` gives every whitespace-separated word of the paragraph's Cyrillic text (the
   same split the web reader uses, so a standalone `—` counts) a start time. The paragraph words
   are aligned again to the cue's transcript words; words without a heard counterpart (dashes,
   dropped words) are interpolated between their neighbours. See `WordTimer`.
5. `wordOffset` is the index of the first paired paragraph word. It is the old way for the web
   reader to find a word span (`i + wordOffset` for `words[i]`); it drifts on dashes and on words
   dropped or merged inside a paragraph. The reader only uses it for cues without `wordStarts`.

The alignment table has one cell per (book word, transcript word); a segment over about 60 million
cells (roughly 7,700 x 7,700 words) is refused with a message to split it.

Diagnostics (unmatched paragraphs and match counts) are printed to stderr.

## Build

    mvn package

This also runs the tests and produces the runnable jar `target/align-0.1.0.jar`. To check that a
change did not alter the matching on real data, run `tools/align/scripts/golden-check.sh data <bookId>` from the repository root
(needs `jq`; see the comment at the top of the script).
