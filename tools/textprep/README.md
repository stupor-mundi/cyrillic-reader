# textprep

Java 21 / Maven tool. Splits the Russian and English source texts into
paragraphs, assigns stable IDs, and transliterates the Russian
(ICU4J `Cyrillic-Latin`).

## Usage

    java -jar target/textprep-0.1.0.jar <dataDir> <bookId>

Example:

    java -jar target/textprep-0.1.0.jar ../../data pushkin_kd

## Input

    <dataDir>/<bookId>/ru.txt
    <dataDir>/<bookId>/en.txt

## Output

    <dataDir>/<bookId>/ru.json   { "lang": "ru", "paragraphs": [ { "id": "ru-000001", "cyr": "...", "lat": "..." } ] }
    <dataDir>/<bookId>/en.json   { "lang": "en", "paragraphs": [ { "id": "en-000001", "text": "..." } ] }

## Processing

- Removes footnote markers of the form `[123]`.
- Removes invisible format characters (Unicode category Cf, BOM) and
  normalizes Unicode space separators to a plain space.
- Normalizes line endings.
- Splits paragraphs on one or more empty lines.
- Splits paragraphs longer than 1200 characters, recursively, at the sentence
  boundary (`. ` `? ` `! `) closest to the middle. If none exists it falls
  back to `; ` / `: `, then `, `, then the nearest space, then the midpoint.

## Build

    mvn package
