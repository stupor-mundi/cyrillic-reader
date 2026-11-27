# segment

Java 21 / Maven tool. Finds chapter headings in `ru.json` and pairs them with
the MP3 files, producing `audio_index.json`.

## Usage

    java -jar target/segment-0.1.0.jar <dataDir> <bookId>

Example:

    java -jar target/segment-0.1.0.jar ../../data pushkin_kd

## Input

    <dataDir>/<bookId>/ru.json
    <dataDir>/<bookId>/audio/*.mp3     (sorted by file name)

## Output

    <dataDir>/<bookId>/audio_index.json

    {
      "bookId": "pushkin_kd",
      "segments": [
        {
          "id": "s001",
          "title": "Глава I",
          "audioFile": "audio/01_glava-1.mp3",
          "startParagraph": "ru-000001",
          "contentStartParagraph": "ru-000002",
          "endParagraph": "ru-000034"
        }
      ]
    }

- A chapter starts at every paragraph whose `cyr` text starts with "Глава"
  (case-insensitive, leading whitespace ignored). `title` is that paragraph's text.
- `startParagraph` is the chapter heading, `contentStartParagraph` is the
  paragraph after it, and `endParagraph` is the last paragraph before the next
  heading (or the end of the book).
- The Nth chapter is paired with the Nth MP3 in file-name order, so name the
  files so that they sort in chapter order (e.g. `01_...`, `02_...`).
- If the counts differ, a warning is printed and chapters without a file get no `audioFile`.

## Build

    mvn package
