# web

Static client-side reader. It loads the JSON files produced by the tools and
controls audio playback, highlighting and scrolling entirely in the browser.
There is no backend at runtime.

## Technology decisions

- **Vite** is the dev server and build tool. It gives fast reload in development
  and a static build.
- **TypeScript** enforces the JSON data contracts (`ru.json`, `en.json`,
  `chunks.json`, `cues`), so field-name mismatches are caught early.
- **No UI framework.** The app renders directly to the DOM; the UI is simple
  (chunk rows, an active chunk, scrolling).
- **Native `<audio>`** provides play, pause and seek. While it plays, a
  `requestAnimationFrame` loop reads `audio.currentTime` to find the active cue and word; `seeked`,
  `play`, `pause` and `ended` keep the highlight right otherwise (see "How sync works").

## Data

All data for a book lives in `web/public/data/<bookId>/`:

    public/data/<bookId>/
      ru.json
      en.json
      chunks.json
      audio_index.json
      audio/<file>.mp3            (paths come from audio_index.json)
      cues/<segmentId>.json       (one per segment, e.g. s001.json)

`ru.json`, `en.json`, `chunks.json` and `audio_index.json` are loaded at startup.
The cues of a segment are loaded when that segment is selected. Paragraph IDs are
global and stable across the book. Cue files reference Russian paragraph IDs
only; the app builds a paragraph ID to chunk ID index at runtime.

The book to load is set by `VITE_BOOK_ID`: copy `.env.example` to `.env.local`
(gitignored) and set it. Nothing book-specific is hardcoded.

## Source files

    src/main.ts         bootstrap, audio element, chapter and speed selectors
    src/types.ts        interfaces for the JSON files
    src/dataLoader.ts   fetches JSON (dev) or reads bundled JSON (file://)
    src/staticData.ts   import.meta.glob of public/data/**, used by the file:// build
    src/audioSync.ts    audio events, active cue/word lookup, highlighting, scrolling
    src/renderer.ts     renders chunk rows (Cyrillic / transliteration / English)
    src/styles.css      layout and styling

## How sync works

- While the audio plays, a `requestAnimationFrame` loop reads `audio.currentTime` every frame and
  adds a lead (`DEFAULT_LEAD_SECONDS` = 0.12 s of wall-clock time, scaled by the playback speed) so the
  highlight appears when the word is heard. Override it with `?lead=0.2` in the page URL to tune by ear.
  The DOM is only touched when the active word changes. `seeked` updates at once; `timeupdate` (about
  4 times per second) is only a fallback while the loop is not running (paused). A binary search finds
  the active cue, then the active word.
- The Cyrillic and transliteration columns are rendered as one `<span class="word">`
  per whitespace-separated word, with `data-paragraph-id` and `data-word-index`.
  The cue's `wordStarts[k]` is the start time of span `k`; the active span is found by binary
  search and highlighted in both columns. Cues without `wordStarts` (older data) fall back to
  span `i + wordOffset` for the transcript word `i`, which drifts.
- Scrolling follows the active word with a smoothed (lerp, factor 0.008)
  `requestAnimationFrame` loop; seeks scroll instantly.
- Paragraphs without a cue are rendered but never highlighted.

## Development

    npm install
    npm run dev

Vite serves the app with hot reload; JSON and audio are served from `public/`.

## Building a static bundle

The static build works from `file://` without a server.

1. Make sure `.env.local` sets `VITE_BOOK_ID`.
2. `public/data/<bookId>/` is a **separate copy** of the book data, not a link to the top-level `data/`.
   After re-running `align` (or any other tool), copy the new files over, e.g.
   `cp data/<bookId>/cues/s*.json web/public/data/<bookId>/cues/`. Make sure it contains `ru.json`, `en.json`, `chunks.json`,
   `audio_index.json` and all `cues/*.json`. `src/staticData.ts` bundles them at build time.
3. Run `npm run build`.
4. Open `dist/index.html` in a browser. Vite copies `public/` into `dist/`, so
   the audio ends up in `dist/data/<bookId>/audio/`. The audio is loaded from
   `./data/<bookId>/<audioFile>` in the static build (`/data/...` in dev).
   Note: the audio is not redistributable unless you have the rights to it.

`vite.config.ts` sets `base: './'` and strips `crossorigin` and `type="module"`
from the built `index.html`, which `file://` needs.
