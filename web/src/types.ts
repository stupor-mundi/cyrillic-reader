// ru.json – Russian translit and paragraphing output
export interface RuParagraph {
  readonly id: string;
  readonly cyr: string;
  readonly lat: string;
}

export interface RuJson {
  readonly lang: "ru";
  readonly paragraphs: readonly RuParagraph[];
}

// en.json – English paragraphing output
export interface EnParagraph {
  readonly id: string;
  readonly text: string;
}

export interface EnJson {
  readonly lang: "en";
  readonly paragraphs: readonly EnParagraph[];
}

// chunks.json – Chunking output
export interface Chunk {
  readonly id: string;
  readonly ru: readonly string[];
  readonly en: readonly string[];
}

export interface ChunksJson {
  readonly bookId: string;
  readonly chunks: readonly Chunk[];
}

// cues.json – Cueing output
export interface Cue {
  readonly paragraphId: string;
  readonly start: number;
  readonly end: number;
}

export interface CuesJson {
  readonly bookId: string;
  readonly audioFile: string;
  readonly cues: readonly Cue[];
}

export type ParagraphId = string;
export type ChunkId = string;
