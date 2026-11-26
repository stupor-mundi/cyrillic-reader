# chunk

Java 21 / Maven tool. Uses an LLM to align Russian and English paragraphs into
display chunks (one row in the reader).

## Usage

    java -jar chunk.jar <dataDir> <bookId>

Example:

    java -jar chunk.jar ../../data pushkin_kd

Requires the `ANTHROPIC_API_KEY` environment variable. The model
(`claude-sonnet-4-6`) and the window sizes are constants in `Main.java`.

## Input / output

    Input:  <dataDir>/<bookId>/ru.json, en.json
    Output: <dataDir>/<bookId>/chunks.json

    { "bookId": "pushkin_kd",
      "chunks": [ { "id": "c000001", "ru": ["ru-000001"], "en": ["en-000001"] } ] }

## How it works

The tool processes the book in windows and calls the LLM once per window.
For each window it sends:

- the Russian paragraphs to consume (about 60; the LLM may end the window at a natural
  boundary between 55 and 65), plus an overlap of 10 following paragraphs as context;
- an English window that starts 15 paragraphs before the English pointer and extends
  120 paragraphs ahead.

To avoid any renumbering, the tool passes the starting chunk ID (for example
`c000121`) and the LLM numbers the chunks sequentially from it. A response looks like:

    {
      "chunks": [
        { "id": "c000121", "ru": ["ru-000101"], "en": ["en-000098"] },
        { "id": "c000122", "ru": ["ru-000102", "ru-000103"], "en": ["en-000099"] }
      ],
      "lastEnUsed": "en-000105"
    }

The tool validates that:

- the first chunk ID is the expected one and the IDs increase by exactly one;
- every Russian paragraph in the consume range appears exactly once and none outside it;
- English IDs come only from the supplied English window and are monotonic;
- `lastEnUsed` matches the highest English paragraph referenced.

If validation fails, the window is retried with stricter instructions (up to
4 attempts in total). HTTP 429 responses are retried with backoff (the org limit
we hit was 30k input tokens per minute). On success the chunks are appended to
`chunks.json`, the Russian pointer advances by the consumed paragraphs and the
English pointer moves to the paragraph after `lastEnUsed`.

## Build

    mvn package
