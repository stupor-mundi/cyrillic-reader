import { loadBook, loadSegmentCues } from "./dataLoader";
import { renderChapter, getChunkElementById } from "./renderer";
import { attachAudioSync } from "./audioSync";

document.addEventListener("DOMContentLoaded", () => {
  void (async () => {
    try {
      const bookId = "pushkin_kd";

      const container = document.getElementById("app");
      if (!container) {
        throw new Error('Container element with id "app" not found');
      }

      const bookData = await loadBook(bookId);
      renderChapter(container, bookData);

      const segments = bookData.audioIndex?.segments ?? [];
      if (segments.length === 0) {
        console.warn("No audio segments found.");
        return;
      }

      const segment = segments[0];
      const cues = await loadSegmentCues(bookId, segment.id);

      const audio = document.createElement("audio");
      audio.controls = true;

      const audioFile = segment.audioFile;
      if (typeof audioFile === "string") {
        if (/^https?:\/\//.test(audioFile) || audioFile.startsWith("/")) {
          audio.src = audioFile;
        } else {
          audio.src = `/data/${bookId}/${audioFile}`;
        }
      }

      container.parentElement?.insertBefore(audio, container);

      attachAudioSync({
        audio,
        cues,
        ruToChunk: bookData.ruToChunk,
        getChunkElById: getChunkElementById,
        onActiveChange: (change) => {
          console.log("Active cue changed:", change);
        },
      });

    } catch (err) {
      console.error("Fatal initialization error:", err);
    }
  })();
});
