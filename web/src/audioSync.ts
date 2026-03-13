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

let lerpTarget: number | null = null;
let rafId: number | null = null;
const LERP_FACTOR = 0.008;

function getTargetScrollY(el: HTMLElement): number {
  const rect = el.getBoundingClientRect();
  const viewportCenter = window.innerHeight / 2;
  return window.scrollY + rect.top + rect.height / 2 - viewportCenter;
}

function lerpTick(): void {
  if (lerpTarget === null) return;
  const current = window.scrollY;
  const diff = lerpTarget - current;
  if (Math.abs(diff) < 0.5) {
    window.scrollTo(0, lerpTarget);
    lerpTarget = null;
    rafId = null;
    return;
  }
  window.scrollTo(0, current + diff * LERP_FACTOR);
  rafId = requestAnimationFrame(lerpTick);
}

function scrollToEl(el: HTMLElement): void {
  lerpTarget = getTargetScrollY(el);
  if (rafId === null) {
    rafId = requestAnimationFrame(lerpTick);
  }
}

function scrollToElInstant(el: HTMLElement): void {
  lerpTarget = null;
  if (rafId !== null) {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  window.scrollTo(0, getTargetScrollY(el));
}

export function attachAudioSync(opts: {
  audio: HTMLAudioElement;
  cues: CuesJson;
  ruToChunk: Map<string, string>;
  getChunkElById: (chunkId: string) => HTMLElement | null;
  onActiveChange?: (change: ActiveChange) => void;
}): () => void {
  const {
    audio,
    cues: cuesData,
    ruToChunk,
    getChunkElById,
    onActiveChange,
  } = opts;
  const cues = cuesData.cues;
  let currentCueIndex = 0;
  let lastActiveChunkId: string | null = null;
  let lastActiveWordSpan: HTMLElement | null = null;
  let justSeeked = false;

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

  function applyActive(cueIndex: number, time: number, instant = false): void {
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
    if (!words || words.length === 0) {
      if (chunkChanged) {
        if (instant) scrollToElInstant(el);
        else scrollToEl(el);
      }
      return;
    }

    const wordIndex = findWordIndex(words, time);
    if (wordIndex < 0) {
      if (chunkChanged) {
        if (instant) scrollToElInstant(el);
        else scrollToEl(el);
      }
      return;
    }

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

    if (allSpans.length > 0) {
      if (instant) scrollToElInstant(el);
      else scrollToEl(allSpans[0]);
    }
  }

  function onTimeUpdate(): void {
    const cueIndex = getActiveCueIndex(audio.currentTime);
    if (cueIndex >= 0) {
      applyActive(cueIndex, audio.currentTime, justSeeked);
    }
    justSeeked = false;
  }

  function onSeeked(): void {
    justSeeked = true;
    const time = audio.currentTime;
    currentCueIndex = Math.max(0, binarySearchCueIndex(cues, time));
    applyActive(currentCueIndex, time, true);
  }


  audio.addEventListener("timeupdate", onTimeUpdate);
  audio.addEventListener("seeked", onSeeked);

  return function detach(): void {
    audio.removeEventListener("timeupdate", onTimeUpdate);
    audio.removeEventListener("seeked", onSeeked);
    if (rafId !== null) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
    lerpTarget = null;
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
