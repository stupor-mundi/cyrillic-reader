const bookId = import.meta.env.VITE_BOOK_ID as string;

const allRu = import.meta.glob("../public/data/*/ru.json", { eager: true });
const allEn = import.meta.glob("../public/data/*/en.json", { eager: true });
const allChunks = import.meta.glob("../public/data/*/chunks.json", { eager: true });
const allAudioIndex = import.meta.glob("../public/data/*/audio_index.json", { eager: true });
const allCues = import.meta.glob("../public/data/*/cues/*.json", { eager: true });

export const staticRu = allRu[`../public/data/${bookId}/ru.json`];
export const staticEn = allEn[`../public/data/${bookId}/en.json`];
export const staticChunks = allChunks[`../public/data/${bookId}/chunks.json`];
export const staticAudioIndex = allAudioIndex[`../public/data/${bookId}/audio_index.json`];

export function getStaticCues(segmentId: string): unknown {
  const key = `../public/data/${bookId}/cues/${segmentId}.json`;
  const mod = allCues[key] as { default: unknown };
  if (!mod) throw new Error(`No static cues for segment: ${segmentId}`);
  return mod.default;
}
