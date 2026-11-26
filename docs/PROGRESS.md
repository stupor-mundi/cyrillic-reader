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

### 2026-11-26 `[idle]`

- Scanned the repo and compared the briefing to the code. Differences found: tool is `textprep`
  (not `translit`); `chunk` makes up to 4 attempts per window; scroll `LERP_FACTOR` is 0.008;
  no "word unchanged" early exit in `audioSync.ts`.
- Rewrote the per-tool READMEs (`textprep`, `segment`, `transcribe`, `align`, `chunk`),
  `web/README.md`, and fixed details in the root `README.md` (docs only, no code changes).
- Added `docs/PROJECT.md`, `docs/PROGRESS.md` and `AGENTS.md`.
- Nothing was built or run on this machine. Check that this clone matches the laptop's git
  history and pull before editing the same files.
