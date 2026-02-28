The system takes raw text files and audio files and transforms them into structured data that the web application can render and synchronize. Russian and English source texts are first split into paragraphs using blank-line separation. Each paragraph receives a stable, language-scoped ID such as ru-000001 or en-000001. These IDs are immutable and represent the fundamental textual units of the system. For Russian paragraphs, transliteration is generated at this stage and stored alongside the original Cyrillic text. The result of this step is two independent JSON files: ru.json (with id, cyr, lat) and en.json (with id, text).

Next, the Russian and English paragraph streams are aligned into chunks. A chunk represents one visual row in the web interface and may contain one or more paragraph IDs from each language. Chunking preserves order and ensures that each paragraph appears exactly once. The chunk layer does not modify paragraph IDs; it only references them. This produces chunks.json, which defines how the two languages are grouped for display.

Audio alignment is performed against the Russian text only. The align tool maps time ranges from an MP3 file to Russian paragraph IDs and produces cues JSON files. Each cue contains a paragraphId along with start and end timestamps in seconds. Audio cues are monotonic and independent of chunk structure. Russian paragraph IDs serve as the canonical anchor for synchronization.

At runtime, the web application loads ru.json, en.json, chunks.json, the relevant cues file, and the corresponding audio file. As the audio plays, the current playback time is matched against the cue list to determine the active Russian paragraph ID. The application then finds the chunk that contains that paragraph and highlights and scrolls that chunk into view. Rendering is driven entirely by JSON data; no pre-generated HTML is required.

The architecture is layered. Paragraph IDs are stable primitives. Transliteration is attached only to Russian paragraphs. Chunk mapping is a higher-level alignment layer that may be regenerated without affecting paragraph IDs. Audio cues reference Russian paragraphs directly and remain valid even if chunking changes. This separation keeps the system flexible, debuggable, and extensible while maintaining a clear canonical reference throughout the pipeline.





folder structure:

```
cyrillic-reader/
  web/                 # static webapp
  tools/
    textprep/          # ru and eng to json, ru translit
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


russian translit and paragraphing output:
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


english paragraphing output:


```
{
  "lang": "en",
  "paragraphs": [
    {
      "id": "en-000001",
      "text": "The officials in their turn looked at me with mockery. The council dispersed. I could not help regretting the weakness of the venerable warrior who, contrary to his own conviction, resolved to follow the opinions of inexperienced and ignorant men."
    },
    {
      "id": "en-000002",
      "text": "A few days after this celebrated council we learned that Pugachev, faithful to his promise, had approached Orenburg. From the height of the city wall I saw the army of the rebels. It seemed to me that their number had increased tenfold since the last assault of which I had been a witness."
    },
    {
      "id": "en-000003",
      "text": "I shall not describe the siege of Orenburg, which belongs to history and not to family memoirs. I will only say briefly that this siege, through the imprudence of the local authorities, was disastrous for the inhabitants, who endured hunger and every possible misery."
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


cueing output:

```
{
  "bookId": "pushkin_kd",
  "audioFile": "01.mp3",
  "cues": [
    {
      "paragraphId": "ru-000001",
      "start": 12.4,
      "end": 28.1
    },
    {
      "paragraphId": "ru-000002",
      "start": 28.1,
      "end": 51.3
    },
    {
      "paragraphId": "ru-000003",
      "start": 51.3,
      "end": 84.0
    }
  ]
}
```

