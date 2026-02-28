The align tool is a Python CLI that generates audio cue data for the web app. It takes one audio segment (e.g., an MP3 for a chapter or part) plus the Russian paragraph stream (ru.json) and produces a cues JSON file that maps audio time ranges to Russian paragraph IDs. The browser does not auto-scroll; the web app controls highlighting and scrolling by comparing the current audio playback time to the cue ranges and then finding the chunk row that contains the referenced Russian paragraph ID.

Inputs: an audio file (audio/<segment>.mp3) and ru.json (Russian paragraphs with stable IDs). Output: cues/<segment>.json containing an ordered list of cue entries of the form { paragraphId, start, end }, where start/end are seconds (floats). Cues reference Russian paragraph IDs only, are monotonic in time, and must not include paragraphs outside the intended segment.

The plan is to produce cues automatically using an audio/text alignment pipeline (ASR plus forced alignment). In practice this means decoding the audio to waveform, generating a timed transcript (word or sentence timestamps), and then mapping timestamps onto paragraph boundaries from ru.json. Paragraph start is the timestamp of the first matched token in that paragraph; paragraph end is the timestamp of the last matched token (or the next paragraph start). The tool may use fuzzy matching when the audiobook and source text differ slightly (punctuation, spelling variants, minor omissions).


align.py

args: --audio audio/01.mp3 --ru ru.json --out cues/01.json

WhisperX/stable-ts/aeneas/MFA CLIs


python align.py <bookId> <segmentId> <audio.mp3> <ru.json> <output.json>

python align.py pushkin_kd s001 audio/s001.mp3 ru.json cues/s001.json

