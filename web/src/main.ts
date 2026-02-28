import { loadBook, loadSegmentCues } from "./dataLoader";
import { renderChapter } from "./renderer";

document.addEventListener("DOMContentLoaded", async () => {
  const bookId = "demo";

  const container = document.getElementById("app");
  if (!container) {
    throw new Error('Container element with id "app" not found');
  }

  const bookData = await loadBook(bookId);
  renderChapter(container, bookData);

  if (bookData.audioIndex.segments.length === 0) {
    console.warn("No audio segments found.");
    return;
  }

  const segment = bookData.audioIndex.segments[0];
  const cues = await loadSegmentCues(bookId, segment.id);
  console.log(cues);
});
