# Progress log

Newest entries first. Tag each entry with the machine: `[laptop]` (personal laptop, main dev
machine with data, audio and test runs) or `[idle]` (the secondary machine, low-risk work only).
Each entry: what changed, which files, what is still open. Read `PROJECT.md` first.

## Next / blocked on the other machine

- `[laptop]` Measure sync error (hand-time about 15 words per chapter) before changing the aligner.
- `[laptop]` Check the renderer vs aligner token count mismatch (standalone `—` etc.) on real data.
- `[laptop]` Web: drive highlight from `requestAnimationFrame` + small lead instead of `timeupdate`.
- `[idle]` Small safe fixes (see "Open small tasks").

### Open small tasks (safe for the idle machine)

- Chapter selector shows `s.id` (e.g. `s001`); show `s.title` instead (`web/src/main.ts`).
- After a chapter switch, `main.ts` builds the audio path without the absolute-URL branch used on first load.
- `web/src/types.ts`: add `words` to `Cue`; remove the cast in `audioSync.ts`.
- `web/src/dataLoader.ts`: `AudioSegment` has a `cuesFile` field that `audio_index.json` doesn't have.
- Root `.gitignore`: ignores every `*.json` (would hide e.g. `web/package.json`); a comment still says "translit tool".
  Prefer ignoring only `data/` and `web/public/data/`.
- Transliteration show/hide toggle.

## Log

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
- Next: tests for `chunk` (validation logic, prompt building) with a faked API client, then refactor out of
  `Main.java`. `align` tests and refactor on the laptop.

### 2026-11-26 `[idle]`

- Scanned the repo and compared the briefing to the code. Differences found: tool is `textprep`
  (not `translit`); `chunk` makes up to 4 attempts per window; scroll `LERP_FACTOR` is 0.008;
  no "word unchanged" early exit in `audioSync.ts`.
- Rewrote the per-tool READMEs (`textprep`, `segment`, `transcribe`, `align`, `chunk`),
  `web/README.md`, and fixed details in the root `README.md` (docs only, no code changes).
- Added `docs/PROJECT.md`, `docs/PROGRESS.md` and `AGENTS.md`.
- Nothing was built or run on this machine. Check that this clone matches the laptop's git
  history and pull before editing the same files.
