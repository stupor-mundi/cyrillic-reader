# align

Java 21 / Maven tool that matches WhisperX word timestamps to Russian paragraphs
and writes a cues JSON file for the web reader.

## Prerequisites

Run the transcribe tool first to produce the words JSON for each segment:
`tools/transcribe/transcribe.py`

## Usage

    java -jar align.jar <dataDir> <bookId> <segmentId>

Example:

    java -jar align.jar ../../data pushkin_kd s001

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
          "words": [ { "text": "Отец", "start": 34.402, "end": 34.822 } ]
        }
      ]
    }

`audioFile` is copied from `audio_index.json`. Paragraphs that are not matched
are omitted.

## How it matches

1. Text is lowercased and stripped of everything except letters.
2. The segment's `startParagraph` (chapter heading) is searched for in the
   transcript as an anchor; matching of the content starts after it.
3. For each paragraph from `contentStartParagraph` to `endParagraph`, it scans up to 300
   transcript words ahead of the current position for a start where the
   paragraph's tokens match. A token matches a word if they are equal or have
   Levenshtein distance of 2 or less (and a length difference of 3 or less).
   The tokens of the next two paragraphs are used as lookahead.
   Mismatches advance the transcript position (and the token position after
   every 3 consecutive mismatches); 10 consecutive mismatches abandon the candidate start.
4. A paragraph is accepted if at least 30% of its tokens matched.
5. A match that starts more than 1 second before the previous cue ended is rejected.
6. `wordOffset` is the index of the first matched paragraph token. The web
   reader adds it to the index of the word in `words` to find the word span. It
   is a patch for WhisperX dropping the first words of a paragraph, and does not
   handle words dropped or merged in the middle.

Diagnostics (unmatched paragraphs and match counts) are printed to stderr.

## Build

    mvn package
