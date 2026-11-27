# Progress log

Newest entries first. Tag each entry with the machine: `[laptop]` (personal laptop, main dev
machine with data, audio and test runs) or `[idle]` (the secondary machine, low-risk work only).
Each entry: what changed, which files, what is still open. Read `PROJECT.md` first.

## Next / blocked on the other machine

- `[laptop]` Measure sync error (hand-time about 15 words per chapter) before changing the aligner.
- `[laptop]` Check the renderer vs aligner token count mismatch (standalone `—` etc.) on real data.
- `[laptop]` Web: drive highlight from `requestAnimationFrame` + small lead instead of `timeupdate`.
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
