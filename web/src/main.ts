import { loadChapter } from "./dataLoader";
import { renderChapter } from "./renderer";

document.addEventListener("DOMContentLoaded", async () => {
  const bookId = "demo";
  const chapterId = "01";

  try {
    const container = document.getElementById("app");
    if (!container) {
      throw new Error('Container element with id "app" not found');
    }

    const data = await loadChapter(bookId, chapterId);
    renderChapter(container, data);
  } catch (err) {
    console.error(err);
  }
});
