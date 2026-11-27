# Progress log

Newest entries first. Tag each entry with the machine: `[laptop]` (personal laptop, main dev
machine with data, audio and test runs) or `[idle]` (the secondary machine, low-risk work only).
Each entry: what changed, which files, what is still open. Read `PROJECT.md` first.

## Next / blocked on the other machine

- `[laptop]` Measure sync error (hand-time about 15 words per chapter) before changing the aligner.
- `[laptop]` Listen-check the new `wordStarts` (sample about 15 words per chapter); the automated check
  agrees with the aligner by construction, so it is not independent proof.
- `[laptop]` Listen to the reader and tune the highlight lead (`?lead=0.05` ... `?lead=0.25` in the URL); set
  `DEFAULT_LEAD_SECONDS` in `web/src/audioSync.ts` to what feels right.
- `[idle]` Small safe fixes (see "Open small tasks").
- Undecided: a parent pom (`tools/pom.xml`) for shared Java and plugin versions. Leaning against it for now (the four
  poms are short and were just aligned). Revisit if more tools are added or the poms drift again; the clean moment
  is after `align` gets its JUnit setup, so all four convert together. A `tools/build-all` script was suggested as a
  lighter alternative.

### Open small tasks (safe for the idle machine)

- Chapter selector shows `s.id` (e.g. `s001`); show `s.title` instead (`web/src/main.ts`).
- After a chapter switch, `main.ts` builds the audio path without the absolute-URL branch used on first load.
- `web/src/types.ts`: add `words` to `Cue`; remove the cast in `audioSync.ts`.
- `web/src/dataLoader.ts`: `AudioSegment` has a `cuesFile` field that `audio_index.json` doesn't have.
- Root `.gitignore`: ignores every `*.json` (would hide e.g. `web/package.json`); a comment still says "translit tool".
  Prefer ignoring only `data/` and `web/public/data/`.
- Transliteration show/hide toggle.

## Log

### 2026-11-27 `[laptop]` (per-word times, matcher rewrite, rAF highlight)

- **rAF highlight (last step of the day):** `web/src/audioSync.ts` runs a `requestAnimationFrame` loop while the
  audio plays, reads `audio.currentTime + lead` (lead 0.12 s wall-clock, scaled by playback speed, `?lead=`
  overrides), and only touches the DOM when the active word changes. `timeupdate` stays as a paused fallback.
  Browser check (dev server, 8 s of playback): 76 of 78 samples had exactly the word the cue data predicts,
  the other 2 were one word off (sampling race), no gaps, highlight stable when paused.
- **Found:** `web/public/data/<bookId>/` is a separate, stale copy of the data (March), so the reader was still
  using the old cues. The new `cues/*.json` were copied in (that copy equalled the original backup before the
  copy). Re-copy after every `align` run.

- **Finding: the audio is not abridged.** Per chapter the transcript has 1.00-1.02x the words of `ru.txt`
  (s014 1.06x), and every "missing" paragraph checked in s011/s012/s013 is in the audio, in order. The
  unmatched paragraphs and wrong cues come from *runaway matches*: one false match far ahead (e.g. s013
  `ru-000521` placed at 6.7 min, really at 3.8) moves the search position past the following paragraphs.
  This corrects the earlier "audio skips" reading of s011-s013.
- **WhisperX vs the book text:** 91.5-96% exact word agreement; most of the rest is spelling (`всё`/`все`,
  `Кузмич`/`Кузьмич`, `Андреич`/`Андреевич`, `Марья`/`Мария`, `ё`), few real errors.
- **Confirmed web-tier drift:** `i + wordOffset` highlighted only 51% of words within 0.3 s, 10% more than 5 s
  off (standalone `—` spans are never spoken, plus dropped/merged words).
- **Fix:** new `WordTimer` in `tools/align`; each cue gets `wordStarts` (one start time per whitespace word of
  the Cyrillic text, interpolated where unheard). `web/src/audioSync.ts` uses it, falling back to the old
  mapping for cues without it. `types.ts`, `README.md`, `tools/align/README.md`, `web/README.md`, `PROJECT.md`
  updated. 14 new tests (80 total). `golden-check.sh` now ignores `wordStarts` and needs `jq`; matching is
  unchanged on all 14 chapters.
- **Data:** `data/pushkin_kd/cues/` regenerated in place (new field only; old fields identical). Backup of the
  previous cues: `data/pushkin_kd/cues.bak-20261127/`.
- Still open: listen-check of the new timings.
- **Matcher rewrite (same day):** `Aligner` now aligns the whole content range to the transcript in one ordered
  pass (`WordTimer.align`, weighted LCS) instead of scanning per paragraph. Result on `pushkin_kd`: 593 of 593
  paragraphs have a cue (was 491), no cue more than 10 s from where an independent search puts it (was 10%
  of cues), no overlaps beyond 0.02 s, whole book aligns in about 6 s. `FuzzyMatcher` is now only used to find
  the heading anchor. Tests: the 1 s time-guard tests were replaced by three tests of the new behaviour (82
  total). `data/pushkin_kd/cues/` regenerated in place; `cues.bak-20261127/` still holds the original cues.
  The 3-10 s band in my independent check is mostly long paragraphs where the check itself is biased, not
  confirmed errors.

### 2026-11-27 `[laptop]` (align characterization tests and refactor)

- Added tests to `tools/align` first (42), then refactored; now 66 tests, all pass. All use invented
  text and invented timings; no book data. Test classes: `TextNormalizerTest`, `FuzzyMatcherIsMatchTest`,
  `FuzzyMatcherTest` (17), `MainEndToEndTest` (9: cue timings, anchor paragraph, time guard incl. its boundary,
  unmatched paragraphs, `wordOffset`, unescaped Cyrillic), `InputLoaderTest` (13), `MainErrorTest` (11, every
  error message), plus the smoke test.
- Split the 439-line `Main.java` into: `Main` (CLI, exit codes, writes the file), `Aligner` (anchor search,
  paragraph loop, time guard, cue building, diagnostics), `FuzzyMatcher` (`isMatch`, `fuzzyMatch`,
  `MatchResult`), `TextNormalizer`, `InputLoader` (words, `ru.json`, `audio_index.json`), `ParagraphIndex`,
  `AlignException`, and the model classes `Word`, `Paragraph`, `Segment`, `Cue`, `CueWord`, `CuesResult`.
- Behaviour unchanged: after every step `mvn test` passed and `golden-check.sh` showed 14 of 14 cue files
  byte-identical to the existing ones; the jar's usage and missing-file messages and exit code 1 are the same.
  One intended difference: the `System.exit` error paths in the logic are now an `AlignException` that
  `Main.main` turns into "print message, exit 1", so they can be tested. `Main.run(dataDir, bookId, segmentId)`
  is the testable entry point.
- Visibility: `FuzzyMatcher`, `TextNormalizer`, `InputLoader` and `Aligner` are package-private.
- Added `tools/align/scripts/golden-check.sh <dataDir> <bookId>`: copies `ru.json`, `audio_index.json` and
  `words/` to a temp dir, runs the jar on every segment, compares the cues byte for byte with `data/.../cues/`.
  It only reads `data/`. Result on `pushkin_kd`: **14 of 14 identical** to the existing cues, so the current
  code reproduces the cues from 13 March exactly. Run it before and after every refactor step.
- Findings that the tests lock in (quirks, not endorsements):
  - On every third consecutive mismatch the matcher also advances the token index (`PROJECT.md` used to say
    mismatches only advance the transcript index). A paragraph whose first word is missing from the
    transcript is not recovered: the matching words are consumed as mismatches and the paragraph is
    unmatched. `wordOffset > 0` appears only when about three unrelated transcript words stand in for a
    dropped token.
  - For a one-token paragraph a scan ends after three mismatches; the search window is 300 words for scan
    starts, not for the first matching word.
  - Any two words of up to two letters match (max distance 2), so short words ("он", "я", "в") match almost
    anything. A suspect for wrong matches and bad `wordOffset`.
  - The time guard rejects a match starting more than 1.0 s before the previous cue ends (exactly 1.0 s is accepted).
- Next for `align`: simulated-ASR-error tests (fake timings, corrupt like WhisperX, compare with truth), then
  the matcher rewrite. Keep running `golden-check.sh`; once the matcher changes on purpose it will report
  differences, and then it tells how many cues moved.

### 2026-11-27 `[laptop]` (history recovered from the author's browser Claude project)

- Docs only. Added "Data and manual work" and "Matcher and design history" sections to `docs/PROJECT.md`
  (what in `data/` is hand-made and must be kept, safe re-run order, run costs, matcher parameters, decisions
  and dead ends). Replaced the old "chapter 1: 8 unmatched" note with real per-chapter match counts computed
  from `ru.json` + `audio_index.json` + `cues/` (491 of 593 paragraphs, worst: s011, s013, s012, s008).
- Nothing under `data/` was changed. No tool was run against `pushkin_kd`.
- Open question: a claim in the history that a `segment` change requires re-transcribing looks wrong
  (`words/` depends only on the MP3s). Not verified by running anything.

### 2026-11-27 `[laptop]` (align JUnit setup)

- `tools/align/pom.xml`: added JUnit 5 (5.11.4, test scope) and Surefire 3.5.2, same as the other tools.
  Added `MainSmokeTest` under `src/test/java/org/stupormundi/align/`. `mvn clean package` passes and the jar builds.
- System `mvn` (3.10.0, Java 21) is now installed on the laptop. Verified `mvn test` passes in `textprep` (21),
  `segment` (14) and `chunk` (57). All four poms are now consistent (parent pom decision still open).
- Open: `align` has only a smoke test. Next is simulated-ASR-error tests before changing the matcher.
- Still untracked: `web/package.json`, `web/package-lock.json`, `web/tsconfig.json` (to add), and the stray
  `tools/transcribe/01_glava-1-serzhant-gvardii.json` (audio-derived, do not commit).

### 2026-11-26 `[idle]` (Java test setup)

- Fixed root `.gitignore`: removed the global `*.json` rule that hid `web/package.json` and
  `web/tsconfig.json`. **Laptop: `git add` those files (and the lockfile) after pulling.**
- Added JUnit 5 (5.11.4) and Surefire 3.5.2 to `tools/textprep`, `tools/segment` and `tools/chunk`,
  each with a `MainSmokeTest` under `src/test/java`. `mvn test` passes in all three.
  `tools/align` is deliberately untouched: add the same dependency and plugin there on the laptop.
- Java 21 is required. On the idle machine the default `JAVA_HOME` is Corretto 11; Corretto 21 is at
  `C:\Program Files\Amazon Corretto\jdk21.0.13_1` (set `JAVA_HOME` per session before running Maven).
- Unused-looking dependencies noticed (not removed): `icu4j` in `segment`, `picocli` and `picocli-codegen` in `chunk`.
- Characterization tests added (they lock current behaviour; they do not assert that it is ideal):
  - `textprep`: `SplitLongParagraphTest` (7) and `MainTest` (5, end to end with a temp dir). 12 pass.
  - `segment`: `BuildSegmentsTest` (10) and `MainTest` (3, includes MP3 listing and pairing). 13 pass.
  - Only visibility changed in the main code: `splitLongParagraph` (`textprep`), `buildSegments` and
    `listAudioFiles` (`segment`) went from `private` to package-private. No behaviour change.
  - `chunk` still has only the smoke test.
- Known quirk locked in by a test: `segment` treats any paragraph starting with "глава" as a chapter
  heading, so a paragraph like "Главарь шайки молчал." would also start a chapter.
- Refactored `textprep` and `segment` out of their single `Main.java` (behaviour unchanged; tests pass, jars build):
  - `textprep`: `Main` (CLI and file I/O), `TextCleaner`, `ParagraphSplitter` (the four duplicated "closest split
    point" loops are now one helper), `ParagraphJson`, `Language`. 21 tests.
  - `segment`: `Main` (CLI and error handling), `AudioIndexGenerator`, `SegmentBuilder`, `AudioFiles`, and the
    JSON model classes `RuBook`, `Paragraph`, `Segment`, `AudioIndex`. 14 tests.
  - One intended difference: `textprep` now writes paragraph keys in a fixed order (`id`, `cyr`, `lat` / `id`,
    `text`); before, the key order was unspecified. The content is the same.
- Refactored `chunk` and added tests (57 pass, jar builds, no real API call made anywhere):
  - Split `Main.java` into: `Main` (CLI), `ChunkAligner` (window loop and retries), `PromptBuilder`,
    `WindowValidator`, `ResponseParser`, `AnthropicClient` (HTTP, 429 backoff), `ChunkIds`, `ChunkSettings`
    (window sizes), `Json`, and the model classes `RuBook`, `EnBook`, `RuParagraph`, `EnParagraph`, `Chunk`,
    `ChunkWindowResponse`.
  - New seam: interface `LlmClient` (`String complete(String prompt)`). `ChunkAligner` takes one, so tests use a
    fake model. `AnthropicClient` is the only real implementation.
  - `PromptBuilder` is checked against golden files (`src/test/resources/org/stupormundi/chunk/*.golden.txt`)
    captured from the code before the split, so the prompt is byte-for-byte unchanged.
  - Tests: `WindowValidatorTest` (26), `ChunkAlignerTest` (11, including multi-window continuation and the retry
    ladder), `ResponseParserTest` (7), `ChunkIdsTest` (6), `AnthropicClientTest` (4), `PromptBuilderTest` (3).
- Quirks found in `chunk` and left as they are (locked in by tests):
  - A reply that is not parseable JSON fails the whole run immediately; only replies that parse but fail
    validation are retried (up to 4 attempts).
  - (Resolved, see "Cleanup" below.) `prompt.txt` was unused and `picocli` was unused.
- Cleanup (`[idle]`, verified with `mvn clean package` in all three tools):
  - Deleted `tools/chunk/src/main/resources/prompt.txt` (nothing read it; it described an older format).
  - `chunk` pom: removed unused `picocli` and `picocli-codegen` and the annotation-processor block; now uses
    the same layout, Jackson (2.18.2) and Shade (3.5.1) versions as the other tools (was Jackson 2.17.2, Shade 3.6.0).
  - `segment` pom: removed unused `icu4j`.
- Next: `align` tests and refactor on the laptop (add the JUnit dependency and Surefire plugin to its pom first,
  then simulated-ASR-error tests before changing the matcher).

### 2026-11-26 `[idle]`

- Scanned the repo and compared the briefing to the code. Differences found: tool is `textprep`
  (not `translit`); `chunk` makes up to 4 attempts per window; scroll `LERP_FACTOR` is 0.008;
  no "word unchanged" early exit in `audioSync.ts`.
- Rewrote the per-tool READMEs (`textprep`, `segment`, `transcribe`, `align`, `chunk`),
  `web/README.md`, and fixed details in the root `README.md` (docs only, no code changes).
- Added `docs/PROJECT.md`, `docs/PROGRESS.md` and `AGENTS.md`.
- Nothing was built or run on this machine. Check that this clone matches the laptop's git
  history and pull before editing the same files.
