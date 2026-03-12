import { loadBook, loadSegmentCues } from "./dataLoader";
import { renderChapter, getChunkElementById } from "./renderer";
import { attachAudioSync, type ActiveChange } from "./audioSync";
import "./styles.css";

document.addEventListener("DOMContentLoaded", () => {
  void (async () => {
    try {
      const bookId = "pushkin_kd";
      const dataBase = window.location.protocol === 'file:' ? './data' : '/data';
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

      const audioBar = document.createElement("div");
      audioBar.id = "audio-bar";
      document.body.insertBefore(audioBar, document.body.firstChild);
      const audio = document.createElement("audio");
      audio.controls = true;

      const audioFile = segment.audioFile;

      if (/^https?:\/\//.test(audioFile) || audioFile.startsWith("/")) {
        audio.src = audioFile;
      } else {
        audio.src = `${dataBase}/${bookId}/${audioFile}`;
      }

      audioBar.appendChild(audio);

      const chapterSelect = document.createElement("select");
      chapterSelect.id = "chapter-select";
      for (const s of segments) {
        const opt = document.createElement("option");
        opt.value = s.id;
        opt.textContent = s.id;
        chapterSelect.appendChild(opt);
      }
      chapterSelect.value = segment.id;
      audio.insertAdjacentElement("afterend", chapterSelect);

      const baseSyncOpts = {
        audio,
        ruToChunk: bookData.ruToChunk,
        getChunkElById: getChunkElementById,
        getWordSpanByIndex: (chunkId: string, wordIndex: number) => {
          const chunkEl = getChunkElementById(chunkId);
          if (!chunkEl) return null;
          const all = chunkEl.querySelectorAll<HTMLElement>(
            `.word[data-word-index="${wordIndex}"]`
          );
          return all[0] ?? null;
        },
        onActiveChange: (change: ActiveChange) => {
          console.log("Active cue changed:", change);
        },
      };

      let detach = attachAudioSync({ ...baseSyncOpts, cues });

      chapterSelect.addEventListener("change", async () => {
        const nextSegment = segments.find((s) => s.id === chapterSelect.value);
        if (!nextSegment) return;
        const nextCues = await loadSegmentCues(bookId, nextSegment.id);

        audio.src = `${dataBase}/${bookId}/${nextSegment.audioFile}`;

        detach();
        detach = attachAudioSync({ ...baseSyncOpts, cues: nextCues });
        audio.currentTime = 0;
        audio.dispatchEvent(new Event("seeked"));
      });


    } catch (err) {
      console.error("Fatal initialization error:", err);
    }
  })();
});
