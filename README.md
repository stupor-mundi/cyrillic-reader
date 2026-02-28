


```
cyrillic-reader/
  web/                 # static webapp (JS/TS)
  tools/
    ingest/            # download/normalize text+audio
    translit/          # generate Latin text
    align/             # generate paragraph cues (VTT/JSON)
  data/                # local working data (gitignored)
  books/               # optional: curated manifests/metadata
  shared/              # schemas, constants, utilities (optional)
  README.md
```






```
{
  "bookId": "pushkin_kd",
  "paragraphs": [
    { "id": "p000001", "cyr": "...", "lat": "..." },
    { "id": "p000002", "cyr": "...", "lat": "..." }
  ]
}
```


