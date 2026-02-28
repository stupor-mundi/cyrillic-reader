
```
java -jar textprep.jar <mode> <input.txt> <output.json>
```

Where <mode> is:

ru → paragraph + transliteration (assumes Russian)
en → paragraph only (English)

java -jar textprep.jar ru data/pushkin_kd/ru.txt data/pushkin_kd/ru.json
java -jar textprep.jar en data/pushkin_kd/en.txt data/pushkin_kd/en.json


