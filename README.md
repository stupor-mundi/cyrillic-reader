# cyrillic-reader

A pipeline for reading Russian literary texts with synchronized audio playback,
bilingual display, and transliteration. The reader highlights the active paragraph
as the audiobook plays.

---

## Pipeline Overview

Source texts and audio are processed through four tools in sequence, producing
JSON artifacts consumed by the web reader.

  ru.txt + en.txt
       ↓
  [1. textprep]  →  ru.json, en.json
       ↓
  [2. chunk]     →  chunks.json
       ↓
  [3. transcribe]  →  words/<segmentId>.json   (one per audio file)
       ↓
  [4. align]     →  cues/<segmentId>.json      (one per audio file)
       ↓
  [web reader]   ←  ru.json + en.json + chunks.json + audio_index.json
                    + cues/<segmentId>.json + audio/<segmentId>.mp3

---

## Tools

### 1. textprep
Java / Maven. Splits Russian and English source texts into paragraphs,
assigns stable IDs, and generates transliteration for Russian.

  java -jar textprep.jar <ruTxt> <enTxt> <outDir>

Example:
  java -jar textprep.jar data/pushkin_kd/ru.txt data/pushkin_kd/en.txt data/pushkin_kd/

Output:
  ru.json
  en.json

---

### 2. chunk
Java / Maven. Uses an LLM to align Russian and English paragraphs into
display chunks. Each chunk is one row in the reader.

  java -jar chunk.jar <dataDir> <bookId>

Example:
  java -jar chunk.jar data pushkin_kd

Input:  ru.json, en.json
Output: chunks.json

---

### 3. transcribe
Python / WhisperX. Transcribes one audio file and writes a flat word list
with timestamps. Run once per audio segment.

  python transcribe.py <audioFile> <outWordsJson>

Example:
  python transcribe.py data/pushkin_kd/audio/01_glava-1.mp3 data/pushkin_kd/words/s001.json

Output:
  words/s001.json

---

### 4. align
Java / Maven. Matches the word timestamps to Russian paragraphs and writes
cue files for the web reader. Run once per segment.

  java -jar align.jar <dataDir> <bookId> <segmentId>

Example:
  java -jar align.jar data pushkin_kd s001

Input:  words/s001.json, ru.json, audio_index.json
Output: cues/s001.json

---

## Data Directory Layout

  data/<bookId>/
    ru.txt                          source Russian text
    en.txt                          source English translation
    ru.json                         Russian paragraphs with transliteration
    en.json                         English paragraphs
    chunks.json                     RU/EN paragraph alignment
    audio_index.json                segment metadata
    audio/
      01_glava-1.mp3                one file per chapter
    words/
      s001.json                     WhisperX word list per segment
    cues/
      s001.json                     timed paragraph cues per segment

---

## JSON Formats

### ru.json
  {
    "lang": "ru",
    "paragraphs": [
      { "id": "ru-000001", "cyr": "...", "lat": "..." }
    ]
  }

### en.json
  {
    "lang": "en",
    "paragraphs": [
      { "id": "en-000001", "text": "..." }
    ]
  }

### chunks.json
  {
    "chunks": [
      { "id": "c000001", "ru": ["ru-000001"], "en": ["en-000001"] }
    ]
  }

### audio_index.json
  {
    "bookId": "pushkin_kd",
    "segments": [
      {
        "id": "s001",
        "title": "Глава I",
        "audioFile": "audio/01_glava-1.mp3",
        "startParagraph": "ru-000001",
        "contentStartParagraph": "ru-000002",
        "endParagraph": "ru-000034"
      }
    ]
  }

### words/s001.json
  [
    { "word": "Отец", "start": 34.402, "end": 34.822 }
  ]

### cues/s001.json
  {
    "bookId": "pushkin_kd",
    "audioFile": "01_glava-1.mp3",
    "cues": [
      {
        "paragraphId": "ru-000002",
        "start": 34.402,
        "end": 47.836,
        "words": [
          { "text": "Отец", "start": 34.402, "end": 34.822 }
        ]
      }
    ]
  }

---

## Web Reader

Static TypeScript/Vite application. No backend required at runtime.

Place all JSON and audio files under web/public/data/<bookId>/.
Run with: npm run dev

Behavior:
- Loads ru.json, en.json, chunks.json, audio_index.json on startup
- Renders the full book as a scrollable list of chunk rows
- Each row shows Russian (Cyrillic), transliteration, and English side by side
- When audio plays, the active paragraph is highlighted and scrolled into view
- Cues for each segment are loaded on demand when that segment is selected
- Paragraphs with no cue are rendered normally but not highlighted
