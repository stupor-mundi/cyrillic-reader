
folder structure:

```
cyrillic-reader/
  web/                 # static webapp
  tools/
    translit/          # RU -> JSON (Java CLI)
    align/             # audio -> cues.json
    chunk/             # RU + EN -> chunks.json (LLM/heuristic)
  data/                # gitignored working data
    pushkin_kd/
      ru.txt
      en.txt
      ru.json
      en.json
      chunks.json
      audio/
        01.mp3
        02.mp3
      cues/
        01.json
        02.json
  books/               # metadata only (small files, versioned)
    pushkin_kd/
      book.json
  shared/              # optional shared schemas
  README.md
```


translit output:
```
{
  "lang": "ru",
  "paragraphs": [
    {
      "id": "ru-000001",
      "cyr": "Чиновники в свою очередь насмешливо поглядели на меня. Совет разошелся. Я не мог не сожалеть о слабости почтенного воина, который, наперекор собственному убеждению, решался следовать мнениям людей несведущих и неопытных.",
      "lat": "Činovniki v svoju očeredʹ nasmešlivo pogljadeli na menja. Sovet razošëlsja. Ja ne mog ne sožaletʹ o slabosti počtennogo voina, kotoryj, naperekor sobstvennomu ubeždeniju, rešalsja sledovatʹ mnenijam ljudej nesveduščih i neopỳtnyh."
    },
    {
      "id": "ru-000002",
      "cyr": "Спустя несколько дней после сего знаменитого совета, узнали мы, что Пугачев, верный своему обещанию, приближился к Оренбургу. ...",
      "lat": "Spustja neskolʹko dnej posle sego znamenitogo soveta, uznali my, čto Pugačëv, vernyj svoemu obeščaniju, priblizilsja k Orenburgu. ..."
    }
  ]
}
```


chunking output:
```
{
  "bookId": "pushkin_kd",
  "chunks": [
    {
      "id": "c000001",
      "ru": ["ru-000001"],
      "en": ["en-000001"]
    },
    {
      "id": "c000002",
      "ru": ["ru-000002", "ru-000003"],
      "en": ["en-000002"]
    },
    {
      "id": "c000003",
      "ru": ["ru-000004"],
      "en": ["en-000003", "en-000004"]
    }
  ]
}
```

