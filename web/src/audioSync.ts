import type { Cue, CuesJson } from "./types";

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

/** Index of the last time that is <= `time` in a non-decreasing list, clamped to the list. */
function findStartIndex(starts: readonly number[], time: number): number {
  let lo = 0;
  let hi = starts.length;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (starts[mid] <= time) lo = mid + 1;
    else hi = mid;
  }
  return Math.max(0, lo - 1);
}

/**
 * Which word span of the cue's paragraph is being spoken at `time`, or -1 if unknown.
 * Cues with `wordStarts` have one time per displayed word. Older cues only have the transcript
 * words, which are mapped to spans by index plus `wordOffset` (this drifts, see PROJECT.md).
 */
function findSpanIndexForCue(cue: Cue, time: number): number {
  if (cue.wordStarts && cue.wordStarts.length > 0) {
    return findStartIndex(cue.wordStarts, time);
  }
  const words = (cue as Cue & { words?: readonly { start: number; end: number }[] }).words;
  if (!words || words.length === 0) return -1;
  const wordIndex = findWordIndex(words, time);
  if (wordIndex < 0) return -1;
  return wordIndex + (cue.wordOffset ?? 0);
}

/**
 * How far ahead of the audio clock the highlight runs, in seconds of wall-clock time. It makes up for
 * the delay between a sample being played and the frame showing it. Override with `?lead=0.2` in the
 * page URL to tune it by ear (allowed range -0.5 to 1).
 */
const DEFAULT_LEAD_SECONDS = 0.12;

function readLeadSeconds(): number {
  const raw = new URLSearchParams(window.location.search).get("lead");
  const value = raw === null ? NaN : parseFloat(raw);
  return Number.isFinite(value) && value >= -0.5 && value <= 1
    ? value
    : DEFAULT_LEAD_SECONDS;
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
  const leadSeconds = readLeadSeconds();
  let syncFrameId: number | null = null;
  let lastAppliedCue = -1;
  let lastAppliedSpan = -2;

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
    const spanIndex = findSpanIndexForCue(cue, time);
    // Called every frame: do nothing (and touch no DOM) while the same word stays active.
    if (cueIndex === lastAppliedCue && spanIndex === lastAppliedSpan) return;
    lastAppliedCue = cueIndex;
    lastAppliedSpan = spanIndex;
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

    if (spanIndex < 0) {
      if (chunkChanged) {
        if (instant) scrollToElInstant(el);
        else scrollToEl(el);
      }
      return;
    }

    const allSpans = document.querySelectorAll<HTMLElement>(
      `.word[data-paragraph-id="${cue.paragraphId}"][data-word-index="${spanIndex}"]`
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

  /** Audio time the highlight should show: the clock plus the lead, scaled by the playback speed. */
  function highlightTime(): number {
    return audio.currentTime + leadSeconds * audio.playbackRate;
  }

  function tick(): void {
    const time = highlightTime();
    const cueIndex = getActiveCueIndex(time);
    if (cueIndex >= 0) {
      applyActive(cueIndex, time, justSeeked);
    }
    justSeeked = false;
  }

  function frame(): void {
    tick();
    syncFrameId = requestAnimationFrame(frame);
  }

  function startFrames(): void {
    if (syncFrameId === null) syncFrameId = requestAnimationFrame(frame);
  }

  function stopFrames(): void {
    if (syncFrameId !== null) {
      cancelAnimationFrame(syncFrameId);
      syncFrameId = null;
    }
  }

  /** Fallback: while the frame loop is not running (paused, or seeking while paused) the coarse
   * `timeupdate` event still keeps the highlight right. */
  function onTimeUpdate(): void {
    if (syncFrameId === null) tick();
  }

  function onSeeked(): void {
    justSeeked = true;
    const time = highlightTime();
    currentCueIndex = Math.max(0, binarySearchCueIndex(cues, time));
    applyActive(currentCueIndex, time, true);
  }

  function onPlay(): void {
    startFrames();
  }

  function onPauseOrEnd(): void {
    stopFrames();
    tick(); // show the exact final position
  }

  audio.addEventListener("timeupdate", onTimeUpdate);
  audio.addEventListener("seeked", onSeeked);
  audio.addEventListener("play", onPlay);
  audio.addEventListener("playing", onPlay);
  audio.addEventListener("pause", onPauseOrEnd);
  audio.addEventListener("ended", onPauseOrEnd);
  if (!audio.paused) startFrames();

  return function detach(): void {
    audio.removeEventListener("timeupdate", onTimeUpdate);
    audio.removeEventListener("seeked", onSeeked);
    audio.removeEventListener("play", onPlay);
    audio.removeEventListener("playing", onPlay);
    audio.removeEventListener("pause", onPauseOrEnd);
    audio.removeEventListener("ended", onPauseOrEnd);
    stopFrames();
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
