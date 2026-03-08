Usage:
java -jar textprep.jar <dataDir> <bookId>

Example:
java -jar textprep.jar ../../data pushkin_kd

Input:
<dataDir>/<bookId>/ru.txt
<dataDir>/<bookId>/en.txt

Output:
<dataDir>/<bookId>/ru.json
<dataDir>/<bookId>/en.json

Where <mode> is:

ru → paragraph + transliteration (assumes Russian)
en → paragraph only (English)

java -jar textprep.jar ru data/pushkin_kd/ru.txt data/pushkin_kd/ru.json
java -jar textprep.jar en data/pushkin_kd/en.txt data/pushkin_kd/en.json

