import type { RuJson, EnJson, ChunksJson, CuesJson } from "./types";

export interface AudioSegment {
  id: string;
  title?: string;
  audioFile: string;
  cuesFile: string;
}

export interface AudioIndexJson {
  bookId: string;
  segments: AudioSegment[];
}

async function fetchJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch ${url}: HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export async function loadBook(bookId: string): Promise<{
  ru: RuJson;
  en: EnJson;
  chunks: ChunksJson;
  audioIndex: AudioIndexJson;
  ruToChunk: Map<string, string>;
}> {
  if (!import.meta.env.DEV) {
    const { staticRu, staticEn, staticChunks, staticAudioIndex } =
      await import("./staticData");
    const ru = staticRu as RuJson;
    const en = staticEn as EnJson;
    const chunks = staticChunks as ChunksJson;
    const audioIndex = staticAudioIndex as unknown as AudioIndexJson;
    const ruToChunk = new Map<string, string>();
    for (const chunk of chunks.chunks) {
      for (const paragraphId of chunk.ru) {
        ruToChunk.set(paragraphId, chunk.id);
      }
    }
    return { ru, en, chunks, audioIndex, ruToChunk };
  }

  const base = `/data/${bookId}`;

  const [ru, en, chunks, audioIndex] = await Promise.all([
    fetchJson<RuJson>(`${base}/ru.json`),
    fetchJson<EnJson>(`${base}/en.json`),
    fetchJson<ChunksJson>(`${base}/chunks.json`),
    fetchJson<AudioIndexJson>(`${base}/audio_index.json`),
  ]);

  const ruToChunk = new Map<string, string>();
  for (const chunk of chunks.chunks) {
    for (const paragraphId of chunk.ru) {
      ruToChunk.set(paragraphId, chunk.id);
    }
  }

  return { ru, en, chunks, audioIndex, ruToChunk };
}

export async function loadSegmentCues(
  bookId: string,
  segmentId: string
): Promise<CuesJson> {
  if (!import.meta.env.DEV) {
    const { getStaticCues } = await import("./staticData");
    return getStaticCues(segmentId) as CuesJson;
  }

  const url = `/data/${bookId}/cues/${segmentId}.json`;
  return fetchJson<CuesJson>(url);
}
