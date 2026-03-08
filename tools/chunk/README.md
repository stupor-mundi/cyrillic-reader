
The chunking tool operates in windows and calls the LLM multiple times. For each window, the tool provides a specific range of Russian paragraphs that must be chunked, along with an English window that includes both continuation paragraphs and contextual overlap. The LLM returns a list of chunks covering exactly the requested Russian consume range.

To avoid any post-processing renumbering, the tool supplies a starting chunk ID with each request. For example, it may specify that chunk IDs must begin at c000121. The LLM is explicitly instructed to number chunks sequentially starting from that ID and to increment by exactly one for each subsequent chunk. It must not skip numbers, reuse IDs, or generate IDs outside the requested range.

A typical response includes a list of chunks with their IDs and a field indicating the last English paragraph ID that was used. The tool validates that:

* The first returned chunk ID matches the expected starting ID.
* All chunk IDs increment sequentially by one.
* Every Russian paragraph in the consume range appears exactly once.
* No Russian paragraph outside the consume range appears.
* English paragraph IDs are valid and monotonic.
* The reported last English ID matches the highest English paragraph referenced.

If validation succeeds, the tool appends the returned chunks directly to the master chunks.json and updates its internal nextChunkId counter. It then advances the Russian pointer by the window size and advances the English pointer to the paragraph after lastEnUsed. If validation fails, the tool retries the window with stricter instructions or adjusted window sizes.

This approach allows the final chunks.json to be assembled incrementally and deterministically without any renumbering step.


```
{
  "chunks": [
    { "id": "c000121", "ru": ["ru-00101"], "en": ["en-00098"] },
    { "id": "c000122", "ru": ["ru-00102","ru-00103"], "en": ["en-00099"] }
  ],
  "lastEnUsed": "en-00105"
}
```

java -jar chunk.jar <dataDir> <bookId>


claude returns:

{
  "chunks": [
    { "id":"c000001", "ru":["ru-000001"], "en":["en-000001"] }
  ],
  "lastEnUsed": "en-000012"
}

