# transcribe

Python / WhisperX. Transcribes one audio file and writes a flat list of words
with timestamps.

## Setup

Use a virtualenv on Python 3.12 (WhisperX/torch did not work on Python 3.14):

    python3.12 -m venv venv
    source venv/bin/activate        # Windows: venv\Scripts\activate
    pip install -r requirements.txt

## Usage

    python transcribe.py <audioFile> <outWordsJson>

Example:

    python transcribe.py ../../data/pushkin_kd/audio/01_glava-1.mp3 \
                         ../../data/pushkin_kd/words/s001.json

Run once per audio segment; the segment IDs come from `audio_index.json`.
This is the slow step, so keep the output.

## Output

    [ { "word": "Отец", "start": 34.402, "end": 34.822 } ]

Words without a start or end timestamp are skipped.

## Settings (hardcoded in transcribe.py)

- model `medium`, device `cpu`, compute type `int8`
- language `ru`, with the WhisperX Russian alignment model for word timestamps
