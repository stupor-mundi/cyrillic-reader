The web application is a static client-side application responsible for rendering synchronized bilingual text and audio. It loads structured JSON files produced by the backend tools and controls audio playback, highlighting, and scrolling entirely in the browser.

Technology decisions:

The web app uses Vite as the development server and build tool. Vite provides fast hot reload during development and produces a static build suitable for simple hosting. TypeScript is used instead of plain JavaScript in order to enforce strong typing for the JSON data contracts (ru.json, en.json, chunks.json, cues.json). This reduces errors caused by field name mismatches and makes refactoring safer as the data model evolves.

No UI framework (React, Vue, etc.) is used. The application renders directly to the DOM using standard browser APIs. The UI requirements are simple: render chunk rows, highlight the active chunk, and scroll into view. A framework would add unnecessary complexity at this stage.

The native HTML <audio> element is used for playback. The browser provides play, pause, and seek controls. The application listens to timeupdate and seeked events to determine which paragraph is active and updates highlighting and scrolling accordingly.

Data loading is done per chapter. Only the JSON and audio files required for the selected chapter are fetched. This keeps memory usage small and simplifies state management.

Paragraph IDs remain global and stable across the entire book. Chunk IDs are stable within the book. Cue files reference Russian paragraph IDs only. The web app resolves paragraphId → chunkId using an index built at runtime.

Folder structure:

web/
index.html
package.json
vite.config.ts
tsconfig.json
public/
data/
<bookId>/
chapters/
01/
ru.json
en.json
chunks.json
cues.json
audio.mp3
src/
main.ts
types.ts
dataLoader.ts
audioSync.ts
renderer.ts
styles.css

Responsibilities of key files:

main.ts: application bootstrap and chapter initialization

types.ts: TypeScript interfaces for ru.json, en.json, chunks.json, cues.json

dataLoader.ts: fetch and parse JSON files

audioSync.ts: handle audio events, resolve active paragraph, manage highlighting

renderer.ts: render chunks into DOM and manage scroll behavior

styles.css: layout and presentation (CSS grid recommended for column layout)

Development workflow:

Run npm install

Run npm run dev

Vite serves the app locally with hot reload

JSON and audio files are served from the public/ directory

The application is entirely static. No backend server is required at runtime. All synchronization logic happens in the browser using the precomputed JSON artifacts.

This design keeps the web layer simple, deterministic, and fully decoupled from the alignment and chunking tools.



## Building a static bundle

To build a self-contained static site (no server required):

1. Copy web/src/staticData.example.ts to web/src/staticData.ts
2. Replace <bookId> with your book ID (e.g. pushkin_kd)
3. Ensure all cues files exist in web/public/data/<bookId>/cues/
4. Run: npm run build
5. The dist/ folder contains the complete static site.
   Open dist/index.html directly in a browser alongside the audio files.
   
