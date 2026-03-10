// Copy this file to staticData.ts and update the paths for your book.
// staticData.ts is gitignored — it is a build-time artifact specific to
// the book you are building the static reader for.

import ruData from "../public/data/<bookId>/ru.json";
import enData from "../public/data/<bookId>/en.json";
import chunksData from "../public/data/<bookId>/chunks.json";
import audioIndexData from "../public/data/<bookId>/audio_index.json";

const cuesModules = import.meta.glob(
  "../public/data/<bookId>/cues/*.json",
  { eager: true }
);

export const staticRu = ruData;
export const staticEn = enData;
export const staticChunks = chunksData;
export const staticAudioIndex = audioIndexData;

export function getStaticCues(segmentId: string): unknown {
  const key = `../public/data/<bookId>/cues/${segmentId}.json`;
  const mod = cuesModules[key] as { default: unknown };
  if (!mod) throw new Error(`No static cues for segment: ${segmentId}`);
  return mod.default;
}

