import type { RuJson, RuParagraph, EnJson, EnParagraph, ChunksJson } from "./types";

export function renderChapter(
  container: HTMLElement,
  data: {
    ru: RuJson;
    en: EnJson;
    chunks: ChunksJson;
    ruToChunk: Map<string, string>;
  }
): void {
  container.replaceChildren();

  const ruById = new Map<string, RuParagraph>();
  for (const p of data.ru.paragraphs) {
    ruById.set(p.id, p);
  }

  const enById = new Map<string, EnParagraph>();
  for (const p of data.en.paragraphs) {
    enById.set(p.id, p);
  }

  for (const chunk of data.chunks.chunks) {
    const chunkDiv = document.createElement("div");
    chunkDiv.dataset.chunkId = chunk.id;
    chunkDiv.id = `chunk-${chunk.id}`;
    chunkDiv.className = "chunk";

    const cyrCol = document.createElement("div");
    cyrCol.className = "col cyr";
    const latCol = document.createElement("div");
    latCol.className = "col lat";
    const enCol = document.createElement("div");
    enCol.className = "col en";

    const cyrTexts = chunk.ru.map((id) => ruById.get(id)?.cyr ?? "").filter(Boolean);
    const latTexts = chunk.ru.map((id) => ruById.get(id)?.lat ?? "").filter(Boolean);
    const enTexts = chunk.en.map((id) => enById.get(id)?.text ?? "").filter(Boolean);

    cyrCol.textContent = cyrTexts.join("\n\n");
    latCol.textContent = latTexts.join("\n\n");
    enCol.textContent = enTexts.join("\n\n");

    chunkDiv.append(cyrCol, latCol, enCol);
    container.appendChild(chunkDiv);
  }
}

export function getChunkElementById(chunkId: string): HTMLElement | null {
  return document.getElementById(`chunk-${chunkId}`);
}
