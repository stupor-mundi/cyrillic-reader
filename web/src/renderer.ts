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

    let globalWordIndex = 0;
    for (let pIndex = 0; pIndex < cyrTexts.length; pIndex++) {
      if (pIndex > 0) {
        cyrCol.appendChild(document.createTextNode("\n\n"));
        latCol.appendChild(document.createTextNode("\n\n"));
      }
      const cyrWords = cyrTexts[pIndex].split(/\s+/).filter(Boolean);
      const latWords = latTexts[pIndex].split(/\s+/).filter(Boolean);
      const maxWords = Math.max(cyrWords.length, latWords.length);
      
      for (let wIndex = 0; wIndex < maxWords; wIndex++) {
        const idx = String(globalWordIndex++);
        
        if (wIndex < cyrWords.length) {
          const span = document.createElement("span");
          span.className = "word";
          span.textContent = cyrWords[wIndex];
          span.dataset.chunkId = chunk.id;
          span.dataset.wordIndex = idx;
          cyrCol.appendChild(span);
          if (wIndex < cyrWords.length - 1)
            cyrCol.appendChild(document.createTextNode(" "));
        }
        
        if (wIndex < latWords.length) {
          const span = document.createElement("span");
          span.className = "word";
          span.textContent = latWords[wIndex];
          span.dataset.chunkId = chunk.id;
          span.dataset.wordIndex = idx;
          latCol.appendChild(span);
          if (wIndex < latWords.length - 1)
            latCol.appendChild(document.createTextNode(" "));
        }
      }
    }
    enCol.textContent = enTexts.join("\n\n");

    chunkDiv.append(cyrCol, latCol, enCol);
    container.appendChild(chunkDiv);
  }
}

export function getChunkElementById(chunkId: string): HTMLElement | null {
  return document.getElementById(`chunk-${chunkId}`);
}
