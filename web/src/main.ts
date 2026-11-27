import { loadBook, loadSegmentCues } from "./dataLoader";
import { renderChapter, getChunkElementById } from "./renderer";
import { attachAudioSync, type ActiveChange } from "./audioSync";
import "./styles.css";

document.addEventListener("DOMContentLoaded", () => {
  void (async () => {
    try {
      const bookId = import.meta.env.VITE_BOOK_ID as string;
      const dataBase = window.location.protocol === 'file:' ? './data' : '/data';
      const container = document.getElementById("app");
      if (!container) {
        throw new Error('Container element with id "app" not found');
      }

      /** URL of a segment's audio: absolute URLs are used as they are, files are looked up under the book. */
      const audioUrl = (audioFile: string): string =>
        /^https?:\/\//.test(audioFile) || audioFile.startsWith("/")
          ? audioFile
          : `${dataBase}/${bookId}/${audioFile}`;

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

      audio.src = audioUrl(segment.audioFile);

      audioBar.appendChild(audio);

      const chapterSelect = document.createElement("select");
      chapterSelect.id = "chapter-select";
      for (const s of segments) {
        const opt = document.createElement("option");
        opt.value = s.id;
        opt.textContent = s.title ? `${s.id.replace(/^s0*/, "")}. ${s.title}` : s.id;
        chapterSelect.appendChild(opt);
      }
      chapterSelect.value = segment.id;
      audio.insertAdjacentElement("afterend", chapterSelect);


      const speedSelect = document.createElement("select");
      speedSelect.id = "speed-select";
      for (let i = 5; i <= 15; i++) {
        const val = (i / 10).toFixed(1);
        const opt = document.createElement("option");
        opt.value = val;
        opt.textContent = `${val}x`;
        if (val === "1.0") opt.selected = true;
        speedSelect.appendChild(opt);
      }
      audioBar.appendChild(speedSelect);
      speedSelect.addEventListener("change", () => {
        const rate = parseFloat(speedSelect.value);
        // The default rate survives a change of `src`; the current rate alone is reset by it.
        audio.defaultPlaybackRate = rate;
        audio.playbackRate = rate;
      });


      const baseSyncOpts = {
        audio,
        ruToChunk: bookData.ruToChunk,
        getChunkElById: getChunkElementById,
        onActiveChange: (change: ActiveChange) => {
          console.log("Active cue changed:", change);
        },
      };

      let detach = attachAudioSync({ ...baseSyncOpts, cues });

      chapterSelect.addEventListener("change", async () => {
        const nextSegment = segments.find((s) => s.id === chapterSelect.value);
        if (!nextSegment) return;
        const nextCues = await loadSegmentCues(bookId, nextSegment.id);

        audio.src = audioUrl(nextSegment.audioFile);

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
