# cyrillic-reader: project briefing

Shared context for anyone (human or AI) working on this repo, on any machine.
Read this first, then the newest entries in `PROGRESS.md`.
If this file conflicts with the code, trust the code and fix this file.

## What it is

A pipeline plus a static web app for reading Russian literature with synchronized
audiobook playback. Each row shows Cyrillic / transliteration / English. The active
paragraph and the active word are highlighted as the audio plays. There is a chapter
selector and a playback speed selector. It is meant for Russian learners.

The only book so far is Pushkin, *The Captain's Daughter* (bookId `pushkin_kd`,
14 chapter-sized MP3s). The repo is public on GitHub, MIT-licensed, version 0.1.0.

## Repo layout

    web/              TypeScript + Vite, no framework, pure DOM
    tools/textprep/   Java 21 + Maven
    tools/chunk/      Java 21 + Maven (calls the Anthropic API)
    tools/segment/    Java 21 + Maven
    tools/transcribe/ Python + WhisperX (own venv)
    tools/align/      Java 21 + Maven
    data/<bookId>/    GITIGNORED: books, audio and all generated JSON
    docs/             this file and PROGRESS.md

- Java tools are called as `java -jar X.jar <dataDir> <bookId> [segmentId]`.
- Base Java package: `org.stupormundi.*`. Each tool keeps all its logic in one `Main.java`.
- The text tool is `textprep` in this repo (an older name was `translit`).
- Per-tool details are in each tool's README. The root README has the pipeline overview.

## Pipeline

1. **textprep**: `ru.txt` + `en.txt` -> `ru.json`, `en.json` (stable IDs `ru-000001` /
   `en-000001`; `lat` = transliteration).
2. **chunk**: -> `chunks.json`. An LLM (Anthropic API, `claude-sonnet-4-6`,
   needs `ANTHROPIC_API_KEY`) aligns RU and EN paragraphs into display rows. Windows of
   about 60 RU paragraphs, 120 EN lookahead. Strict validation, up to 4 attempts per window,
   backoff on HTTP 429 (org limit 30k input tokens per minute).
3. **segment**: -> `audio_index.json`. Chapters are paragraphs starting with "Глава",
   paired with MP3s in file-name order.
4. **transcribe**: `python transcribe.py <mp3> <words/sNNN.json>`. WhisperX, model
   "medium", CPU, int8, language "ru". Slow: run once per chapter and keep the output.
5. **align**: `words/sNNN.json` + `ru.json` + `audio_index.json` -> `cues/sNNN.json`.
   Fuzzy matching (Levenshtein distance 2, lookahead over the next two paragraphs).
6. **web**: loads all the JSON and plays the audio.

## Data formats

    ru.json          {lang, paragraphs:[{id, cyr, lat}]}
    en.json          {lang, paragraphs:[{id, text}]}
    chunks.json      {bookId, chunks:[{id:"c000001", ru:[ids], en:[ids]}]}
    audio_index.json {bookId, segments:[{id:"s001", title, audioFile, startParagraph,
                      contentStartParagraph, endParagraph}]}
    words/sNNN.json  [{word, start, end}]
    cues/sNNN.json   {bookId, audioFile, cues:[{paragraphId, start, end, wordOffset,
                      words:[{text, start, end}]}]}

Paragraphs that don't match are omitted from the cues. The web app still renders them
but never highlights them.

## Web app behaviour

- `audioSync.ts` listens to `timeupdate` and `seeked`, binary-searches the active cue, then
  finds the active word. It highlights spans by `data-paragraph-id` + `data-word-index`
  in both the Cyrillic and the translit column, at index `i + wordOffset`.
- Scrolling is a lerp driven by `requestAnimationFrame` (`LERP_FACTOR` 0.008) that follows
  the active word. Seeks jump instantly.
- `.env.local` holds `VITE_BOOK_ID` (gitignored; `.env.example` is committed). Nothing
  book-specific is hardcoded.
- Two modes: `npm run dev`, and a static build (`dist/index.html` opened via `file://`).
  For `file://`, `vite.config.ts` uses `base: './'` and strips `crossorigin` and
  `type="module"`; `dataLoader.ts` reads bundled JSON from `staticData.ts`
  (`import.meta.glob`). The audio path uses `dataBase` (`/data` in dev, `./data` in static).

## Current state

Everything works end to end: all 14 chapters were transcribed and aligned, and the cues
copied into the web app.

**Main open problem: sync quality.** Match rates vary by chapter (chapter 2: 38 of 43
paragraphs matched; chapter 1: 8 unmatched, mostly short dialogue). The highlight
sometimes lags the audio.

### Where timing goes wrong (suspects, most likely first)

1. **Web layer.** `timeupdate` fires about 4 times a second and there is no lead offset,
   so the highlight can trail the audio by up to about 250 ms.
2. **Align, wrong words.** `wordOffset` only handles words dropped at the start of a
   paragraph. A word dropped, merged or split mid-paragraph shifts the rest of the paragraph.
3. **Tokenization mismatch (suspected, not confirmed on real data).** `renderer.ts` makes
   one span per whitespace-separated word, including standalone `—`; the aligner's
   `normalizeAndTokenize` drops tokens that normalize to empty, so indices can drift.
4. **Align, coverage.** Unmatched paragraphs get no cue, so nothing is highlighted there.

WhisperX itself was fine. Early "0 matches" bugs were in the matching code. WhisperX
mishears proper nouns (e.g. "Гринев" -> "горенев"), so the matcher must tolerate that.

## Insights not visible in the code

- **Why `wordOffset` exists.** WhisperX sometimes drops the first words of a paragraph
  (e.g. "Дорожные размышления"), so the highlight ran behind. The aligner records the number
  of leading paragraph tokens not matched. It is a patch, not a fix.
- **Matcher history.** Started as exact equality in Python, rewritten in Java, now
  Levenshtein plus lookahead. Mismatches must only advance the transcript index, not the
  token index (a real past bug). The Python aligner is gone.
- **Python environment.** WhisperX/torch broke on Python 3.14. Use a venv on Python 3.12.
  A torchcodec warning is harmless.
- **Copyright.** The Pushkin text is public domain. The audio came from deti-online.com
  and is almost certainly NOT redistributable: never commit it or the derived word
  timestamps. A public demo needs a clean recording (LibriVox was the idea; a Russian one
  was not confirmed).
- **Environment.** Main dev machine: CachyOS (Arch), IntelliJ for Java, Cursor for edits.
  Prompts to Cursor work best in the form "change X, change nothing else".

## Priorities

1. **Sync timing and alignment quality.**
   - Measure first: hand-time about 15 words per chapter; report median and worst error.
   - Cheap web fix: drive the highlight from `requestAnimationFrame` reading
     `audio.currentTime` instead of `timeupdate`, with a lead of about 100-150 ms.
   - Replace the matcher with a chapter-wide sequence alignment of the book's words to the
     transcript (Needleman-Wunsch style, fuzzy or phonetic similarity). Every book word
     gets a time (interpolated where unheard), `wordOffset` goes away, and unmatched
     paragraphs get approximate timing.
   - Optionally refine with CTC forced alignment inside located paragraphs.
   - Use an LLM only for low-confidence paragraphs and for normalizing spoken forms
     (numbers, initials).
2. **Java refactor + tests.** JUnit 5 tests locking current behaviour first, then split
   the big `Main.java` files.
3. **UI ideas** (after good alignment): translit show/hide toggle; fading translit of
   spoken words; a fixed "karaoke bar" with the current word large.
4. **A demo:** a screen recording (the audio can't be shared) or a separate pre-built
   static repo with a legal sample.

### Ways to evaluate sync without a human judge

- Renderer vs aligner token-count test per paragraph (needs only `ru.json`).
- Simulated ASR errors: give the book text fake timings, corrupt it like WhisperX does
  (drops, merges, splits, mangled names), run the aligner, compare to the known truth.
- TTS audio with known word timings, run through the whole pipeline.
- Per-word acoustic confidence from CTC forced alignment; agreement between aligners;
  round-trip re-transcription of each cue's audio.
- Proxy metrics from existing files: match rate, overlaps and gaps, speech-rate outliers.

### Useful supporting tools (the author can't read Russian well)

Debug viewer in the web app (timestamps, confidence, unmatched in red, "play this word");
waveform with word boxes; per-chapter report from `align` with RU and EN side by side;
transcript-vs-book diff view; word-level glosses.

## Ground rules

- Don't break the `file://` static build or dev mode.
- Keep the JSON contracts above stable, or update the READMEs and `web/src/types.ts` together.
- Prefer small, focused changes.
- Two machines are used. See `PROGRESS.md` for the log and the split of work: this
  (idle-time) machine does low-risk tasks, the personal laptop does anything that needs
  data, audio or test runs. Avoid editing `tools/align/` from the idle machine.
