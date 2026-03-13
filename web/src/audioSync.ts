import type { CuesJson } from "./types";

export type ActiveChange = {
  paragraphId: string;
  chunkId: string;
  cueIndex: number;
};

function binarySearchCueIndex(
  cues: readonly { start: number }[],
  time: number
): number {
  let lo = 0;
  let hi = cues.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (cues[mid].start <= time) lo = mid + 1;
    else hi = mid;
  }
  return lo - 1;
}

function findWordIndex(
  words: readonly { start: number; end: number }[],
  time: number
): number {
  if (words.length === 0) return -1;
  let lo = 0;
  let hi = words.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (words[mid].start <= time) lo = mid + 1;
    else hi = mid;
  }
  let idx = lo - 1;
  if (idx < 0) idx = 0;
  else if (idx >= words.length) idx = words.length - 1;
  return idx;
}

export function attachAudioSync(opts: {
  audio: HTMLAudioElement;
  cues: CuesJson;
  ruToChunk: Map<string, string>;
  getChunkElById: (chunkId: string) => HTMLElement | null;
  getWordSpanByIndex: (chunkId: string, wordIndex: number) => HTMLElement | null;
  onActiveChange?: (change: ActiveChange) => void;
}): () => void {
  const {
    audio,
    cues: cuesData,
    ruToChunk,
    getChunkElById,
    getWordSpanByIndex,
    onActiveChange,
  } = opts;
  const cues = cuesData.cues;
  let currentCueIndex = 0;
  let lastActiveChunkId: string | null = null;
  let lastActiveWordSpan: HTMLElement | null = null;

  function getActiveCueIndex(time: number): number {
    if (cues.length === 0) return -1;
    while (
      currentCueIndex < cues.length - 1 &&
      time >= cues[currentCueIndex].end
    ) {
      currentCueIndex++;
    }
    while (currentCueIndex > 0 && time < cues[currentCueIndex].start) {
      currentCueIndex--;
    }
    const c = cues[currentCueIndex];
    if (time < c.start || time >= c.end) return -1;
    return currentCueIndex;
  }

  function applyActive(cueIndex: number, time: number): void {
    if (cueIndex < 0 || cueIndex >= cues.length) return;
    const cue = cues[cueIndex];
    const chunkId = ruToChunk.get(cue.paragraphId);
    if (chunkId == null) return;
    const el = getChunkElById(chunkId);
    if (!el) return;

    const chunkChanged = lastActiveChunkId !== chunkId;
    if (lastActiveChunkId !== null && lastActiveChunkId !== chunkId) {
      getChunkElById(lastActiveChunkId)?.classList.remove("active");
    }
    if (chunkChanged && lastActiveWordSpan !== null) {
      document
        .querySelectorAll<HTMLElement>(
          `.word[data-chunk-id="${lastActiveChunkId}"][data-word-index="${lastActiveWordSpan.dataset.wordIndex}"]`
        )
        .forEach((s) => s.classList.remove("word-active"));
      lastActiveWordSpan = null;
    }
    lastActiveChunkId = chunkId;
    el.classList.add("active");
    if (chunkChanged) el.scrollIntoView({ block: "center" });
    if (chunkChanged)
      onActiveChange?.({
        paragraphId: cue.paragraphId,
        chunkId,
        cueIndex,
      });

    const cueWithWords = cue as typeof cue & {
      words?: readonly { start: number; end: number }[];
    };
    const words = cueWithWords.words;
    if (!words || words.length === 0) return;

    const wordIndex = findWordIndex(words, time);
    if (wordIndex < 0) return;

    const offset = cue.wordOffset ?? 0;
    const offsetIndex = wordIndex + offset;
    const allSpans = document.querySelectorAll<HTMLElement>(
      `.word[data-paragraph-id="${cue.paragraphId}"][data-word-index="${offsetIndex}"]`
    );

    // Remove from previous
    if (lastActiveWordSpan !== null) {
      document
        .querySelectorAll<HTMLElement>(
          `.word[data-paragraph-id="${lastActiveWordSpan.dataset.paragraphId}"][data-word-index="${lastActiveWordSpan.dataset.wordIndex}"]`
        )
        .forEach((s) => s.classList.remove("word-active"));
      lastActiveWordSpan = null;
    }

    // Apply to all matching spans (cyr + lat)
    if (allSpans.length > 0) {
      allSpans.forEach((s) => s.classList.add("word-active"));
      lastActiveWordSpan = allSpans[0];
    }
  }

  function onTimeUpdate(): void {
    const cueIndex = getActiveCueIndex(audio.currentTime);
    if (cueIndex >= 0) applyActive(cueIndex, audio.currentTime);
  }


  function onSeeked(): void {
    const time = audio.currentTime;
    currentCueIndex = Math.max(0, binarySearchCueIndex(cues, time));
    applyActive(currentCueIndex, time);
  }


  audio.addEventListener("timeupdate", onTimeUpdate);
  audio.addEventListener("seeked", onSeeked);

  return function detach(): void {
    audio.removeEventListener("timeupdate", onTimeUpdate);
    audio.removeEventListener("seeked", onSeeked);
    if (lastActiveChunkId != null) {
      getChunkElById(lastActiveChunkId)?.classList.remove("active");
    }
    if (lastActiveWordSpan !== null) {
      document
        .querySelectorAll<HTMLElement>(
          `.word[data-paragraph-id="${lastActiveWordSpan.dataset.paragraphId}"][data-word-index="${lastActiveWordSpan.dataset.wordIndex}"]`
        )
        .forEach((s) => s.classList.remove("word-active"));
      lastActiveWordSpan = null;
    }
  };
}
