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
- Base Java package: `org.stupormundi.*`. `Main` is only the CLI; the logic is split into small classes
  per tool, with JUnit 5 tests (`mvn test`).
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
   Chapter-wide ordered alignment of the book words to the transcript (since 2026-11-27; earlier: per-paragraph fuzzy scan, Levenshtein distance 2, lookahead over the next two paragraphs).
6. **web**: loads all the JSON and plays the audio.

## Data formats

    ru.json          {lang, paragraphs:[{id, cyr, lat}]}
    en.json          {lang, paragraphs:[{id, text}]}
    chunks.json      {bookId, chunks:[{id:"c000001", ru:[ids], en:[ids]}]}
    audio_index.json {bookId, segments:[{id:"s001", title, audioFile, startParagraph,
                      contentStartParagraph, endParagraph}]}
    words/sNNN.json  [{word, start, end}]
    cues/sNNN.json   {bookId, audioFile, cues:[{paragraphId, start, end, wordOffset,
                      words:[{text, start, end}], wordStarts:[start per displayed word]}]}

Paragraphs that don't match are omitted from the cues. The web app still renders them
but never highlights them.

## Web app behaviour

- `audioSync.ts` listens to `timeupdate` and `seeked`, binary-searches the active cue, then
  finds the active word. It highlights spans by `data-paragraph-id` + `data-word-index`
  in both the Cyrillic and the translit column, at the index found in the cue's `wordStarts`
  (older cues without it: `i + wordOffset`).
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

**Main open problem: sync quality.** Match rates vary by chapter. Counted on 2026-11-27 from
`ru.json` + `audio_index.json` + `cues/` (paragraphs between `contentStartParagraph` and
`endParagraph` that have a cue): **before the 2026-11-27 matcher rewrite** 491 of 593 (83%), with about 10% of
those cues more than 10 s off (runaway matches; worst chapters s011, s013, s012, s008). **After the rewrite
(chapter-wide alignment) all 593 paragraphs have a cue** and none is more than 10 s from where an independent
search of the transcript puts it. The audio itself is not abridged (word counts match `ru.txt` within 2%, s014
6%); the old gaps were matcher failures. Residual risk: cues for paragraphs the transcript heard badly are
approximate, and the new cues have not been listened to yet.

### Where timing goes wrong (suspects, most likely first)

1. **Web layer (fixed 2026-11-27).** `timeupdate` fires about 4 times a second and there was no lead
   offset, so the highlight could trail the audio by up to about 250 ms. Now driven by
   `requestAnimationFrame` with a 120 ms lead (`?lead=` in the URL to tune). Not yet judged by ear.
2. **Align, wrong words.** `wordOffset` only handles words dropped at the start of a
   paragraph. A word dropped, merged or split mid-paragraph shifts the rest of the paragraph.
3. **Tokenization mismatch (confirmed on real data 2026-11-27, fixed by `wordStarts`).**
   `renderer.ts` makes one span per whitespace-separated word, including standalone `—`; the
   transcript words never line up with those one to one. With the old `i + wordOffset` mapping
   only 51% of words were highlighted within 0.3 s of their true time, 10% were more than 5 s
   off (checked on 359 correctly placed cues). Skipping dashes alone would have given 72%.
4. **Align, coverage and wrong cues (fixed 2026-11-27).** The old per-paragraph scan lost whole runs of
   paragraphs after one false match far ahead; replaced by a chapter-wide alignment.

WhisperX itself was fine. Early "0 matches" bugs were in the matching code. WhisperX
mishears proper nouns (e.g. "Гринев" -> "горенев"), so the matcher must tolerate that.

## Data and manual work (lives outside the repo)

The example book's inputs and outputs are in `data/<bookId>/`, which is gitignored and has no backup
in git. Treat it as precious. Do not delete, overwrite or regenerate anything there without asking, and
copy the existing files aside before running a tool on a real book.

- **Hand-made inputs** (cannot be regenerated): `ru.txt` and `en.txt`. `ru.txt` was cut down by hand from the
  raw Wikisource export (`ru (orig).txt`): front matter, page-number lines, editor footnotes ("— Ред."), the
  editor's epilogue and the appendix "Пропущенная глава" were removed. Chapter epigraphs were also removed
  (this caused some matching trouble; not recorded whether they were put back). Chapter headings stay as
  standalone paragraphs ("Глава I…", "ГЛАВА II…"): `segment` needs them. `en.txt` is the English
  Wikisource text ("Russian Romance" translation, per the file name) with front matter and translator
  footnotes removed and 14 headings normalised to `CHAPTER N. TITLE.`; the translation body is unchanged
  (checked against the raw export). `ru_small.json` and `en_small.json` are small slices made to smoke-test `chunk`.
- **Never hand-edited** (as far as known): `ru.json`, `en.json`, `chunks.json`, `audio_index.json`,
  `words/`, `cues/`. All tool output. Mixed-case chapter titles ("Глава I…" vs "ГЛАВА II…") come from the
  source headings; `segment` detects headings case-insensitively and copies them.
- **Safe re-run order:** `textprep` -> `chunk` -> `segment` -> `align`. Changing `ru.txt` or the paragraph
  splitting renumbers paragraphs, which invalidates `chunks.json`, `audio_index.json` and the cues.
  `words/` depends only on the MP3 files and their order, not on `ru.json`: run `transcribe` again only if the
  audio changes.
- **Cost of each step** (estimates from file timestamps and the history, not measured): `textprep`, `segment`,
  `align`: seconds to about two minutes for the whole book. `transcribe`: roughly 6-11 minutes per chapter on
  CPU (about 100 minutes for 13 chapters). `chunk`: hours with 429 backoff, and it costs API money.
- **Audio** (file names `01_glava-1-serzhant-gvardii.mp3` ... `14_glava-14-sud.mp3`) maps by sorted order to
  `s001`-`s014`. No trimming or merging is recorded. The audio speaks extras that are not in the text (author,
  title, a proverb, "Глава первая", the chapter title, the epigraph; chapter 2 starts with "Дорожные
  размышления"). The aligner skips this by starting at the chapter-heading paragraph (`startParagraph`).
  An `entryHint` field was proposed and never implemented.
- **Stray file:** `tools/transcribe/01_glava-1-serzhant-gvardii.json` (untracked, has per-word scores) is raw
  WhisperX CLI output from a manual test on chapter 1. `words/s001.json` has no scores, so it came from
  `transcribe.py`. Keep it, do not commit it.

## Matcher and design history (decisions and dead ends)

- **Current `align` method** (`Aligner.java`, `WordTimer.java`): the heading paragraph is found as an anchor with
  the old fuzzy scan (`FuzzyMatcher.java`: Levenshtein 2, window 300); then the content range is aligned to the
  transcript in one ordered pass (weighted LCS; exact 2, fuzzy 1; `ё` folded; short words only exact). A paragraph
  gets a cue when at least 30% of its words are paired; pairings more than 15 transcript words from the rest of
  the paragraph are dropped. *Replaced on 2026-11-27:* the per-paragraph scan with a 300-word window, 9-mismatch
  cut-off, 2-paragraph lookahead and the 1 s time guard (it ran away on false matches; see PROGRESS.md).
- **Rejected:** Soundex and Metaphone (English-specific); exact string matching; the first Python aligner
  (slow, because it re-transcribed on every run; plus the both-indices bug); a stub aligner that divided
  the audio duration evenly across paragraphs; `books/` and `ingest/` tools.
- **Decided:** Java for the text-heavy tools (the author already used Maven, IntelliJ and Jackson); Python only
  for WhisperX; one `Main.java` per tool at first (works well with Cursor), refactor later; `wordOffset`
  instead of padding the words array, because it keeps the real timestamps; `.env.local` instead of a
  `config.json`; `staticData.ts` is committed as application code; `data-paragraph-id` selectors (an
  earlier `data-chunk-id` selector highlighted several paragraphs at once).
- **WhisperX settings:** `medium`, CPU, `int8` (`float16` failed on CPU), language `ru`. No other model or
  audio source was tried; a larger model or GPU was only suggested.
- **`chunk` history:** model `claude-3-5-sonnet-latest` returned 404, now `claude-sonnet-4-6`; org limit 30k
  input tokens per minute; 429 backoff of 15 s then 30 s; defaults were 80 RU / 150 EN lookahead, now about
  60 RU (55-65 allowed) and 120 EN, with a 10-paragraph RU and 15-paragraph EN overlap.
- **`textprep` cleanup order:** strip `[n]` markers, format characters and BOM, space variants, CRLF, split on
  blank lines, split paragraphs over 1200 characters (`MAX_PARAGRAPH_CHARS`).
- **Static build also needs:** all 14 cue files present for `import.meta.glob`, and the `justSeeked` flag that
  suppresses the slow scroll after a seek. The scroll `LERP_FACTOR` was 0.04 before it became 0.008 (the reason
  is not recorded).
- **Unverified:** the audio source (deti-online.com) was judged "almost certainly under copyright" and was
  not verified; one LibriVox check found no Russian recording of this book; no other source was checked.

## Insights not visible in the code

- **Why `wordOffset` exists.** WhisperX sometimes drops the first words of a paragraph
  (e.g. "Дорожные размышления"), so the highlight ran behind. The aligner records the number
  of leading paragraph tokens not matched. It is a patch, not a fix.
- **Matcher history.** Started as exact equality in Python, rewritten in Java, now
  Levenshtein plus lookahead. A single mismatch must only advance the transcript index,
  not the token index (a real past bug). The current code also advances the token index on every third
  consecutive mismatch (locked in by `FuzzyMatcherTest`). The Python aligner is gone.
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
   - Done 2026-11-27: highlight driven from `requestAnimationFrame` reading `audio.currentTime`, with a
     lead of 0.12 s (tunable). Still to do: choose the final lead by listening.
   - Done 2026-11-27: chapter-wide sequence alignment of the book's words to the transcript, and a
     time for every book word (`wordStarts`, interpolated where unheard). `wordOffset` is only kept for
     old cues. Still to do: phonetic similarity or an LLM pass for badly heard words.
   - Optionally refine with CTC forced alignment inside located paragraphs.
   - Use an LLM only for low-confidence paragraphs and for normalizing spoken forms
     (numbers, initials).
2. **Java refactor + tests.** Done for `textprep`, `segment`, `chunk` and `align` (tests locked current
   behaviour first, then `Main.java` was split). For `align`, run `tools/align/scripts/golden-check.sh`
   after any change to compare against existing cues on the laptop.
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
