# Agent instructions

- Before starting, read `docs/PROJECT.md` and the newest entries (and the "Next" section) of `docs/PROGRESS.md`.
- At the end of a session, append a short dated entry to `docs/PROGRESS.md`, tagged `[laptop]` or `[idle]`
  (which machine you are on), and update the "Next" section if needed.
- Prefer small, focused changes: change what was asked and nothing else.
- Don't break the `file://` static build or dev mode. Keep the JSON contracts stable, or update the READMEs and
  `web/src/types.ts` together.
- Never commit audio or word timestamps derived from it (copyright). `data/` is gitignored.
- On the `[idle]` machine, avoid editing `tools/align/`.
