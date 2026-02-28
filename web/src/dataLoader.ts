import type { RuJson, EnJson, ChunksJson, CuesJson } from "./types";

export async function loadChapter(
  bookId: string,
  chapterId: string
): Promise<{
  ru: RuJson;
  en: EnJson;
  chunks: ChunksJson;
  cues: CuesJson;
  ruToChunk: Map<string, string>;
}> {
  const dir = `/data/${bookId}/chapters/${chapterId}`;

  const [ruRes, enRes, chunksRes, cuesRes] = await Promise.all([
    fetch(`${dir}/ru.json`),
    fetch(`${dir}/en.json`),
    fetch(`${dir}/chunks.json`),
    fetch(`${dir}/cues.json`),
  ]);

  if (!ruRes.ok || !enRes.ok || !chunksRes.ok || !cuesRes.ok) {
    throw new Error("Failed to fetch chapter data");
  }

  const [ru, en, chunks, cues] = await Promise.all([
    ruRes.json() as Promise<RuJson>,
    enRes.json() as Promise<EnJson>,
    chunksRes.json() as Promise<ChunksJson>,
    cuesRes.json() as Promise<CuesJson>,
  ]);

  const ruToChunk = new Map<string, string>();
  for (const chunk of chunks.chunks) {
    for (const paragraphId of chunk.ru) {
      ruToChunk.set(paragraphId, chunk.id);
    }
  }

  return { ru, en, chunks, cues, ruToChunk };
}
