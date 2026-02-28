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

export function attachAudioSync(opts: {
  audio: HTMLAudioElement;
  cues: CuesJson;
  ruToChunk: Map<string, string>;
  getChunkElById: (chunkId: string) => HTMLElement | null;
  onActiveChange?: (change: ActiveChange) => void;
}): () => void {
  const { audio, cues: cuesData, ruToChunk, getChunkElById, onActiveChange } =
    opts;
  const cues = cuesData.cues;
  let currentCueIndex = 0;
  let lastActiveChunkId: string | null = null;

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

  function applyActive(cueIndex: number): void {
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
    lastActiveChunkId = chunkId;
    el.classList.add("active");
    if (chunkChanged) el.scrollIntoView({ block: "center" });
    if (chunkChanged)
      onActiveChange?.({
        paragraphId: cue.paragraphId,
        chunkId,
        cueIndex,
      });
  }

  function onTimeUpdate(): void {
    const cueIndex = getActiveCueIndex(audio.currentTime);
    if (cueIndex >= 0) applyActive(cueIndex);
  }

  function onSeeked(): void {
    const time = audio.currentTime;
    currentCueIndex = binarySearchCueIndex(cues, time);
    if (
      currentCueIndex >= 0 &&
      time >= cues[currentCueIndex].start &&
      time < cues[currentCueIndex].end
    ) {
      applyActive(currentCueIndex);
    }
  }

  audio.addEventListener("timeupdate", onTimeUpdate);
  audio.addEventListener("seeked", onSeeked);

  return function detach(): void {
    audio.removeEventListener("timeupdate", onTimeUpdate);
    audio.removeEventListener("seeked", onSeeked);
    if (lastActiveChunkId != null) {
      getChunkElById(lastActiveChunkId)?.classList.remove("active");
    }
  };
}
