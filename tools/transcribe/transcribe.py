#!/usr/bin/env python3
"""Run WhisperX on an audio file and write a flat word list to JSON."""

import json
import os
import sys


def main() -> None:
    usage = "Usage: python transcribe.py <audioFile> <outWordsJson>"
    if len(sys.argv) != 3:
        print(usage, file=sys.stderr)
        sys.exit(1)

    audio_path, out_words_json = sys.argv[1], sys.argv[2]

    try:
        import whisperx
    except ImportError:
        print("Error: whisperx is not installed. Install it with: pip install whisperx", file=sys.stderr)
        sys.exit(1)

    device = "cpu"
    model = whisperx.load_model("medium", device, language="ru", compute_type="int8")
    audio = whisperx.load_audio(audio_path)
    result = model.transcribe(audio, language="ru")
    align_model, align_metadata = whisperx.load_align_model(language_code="ru", device=device)
    result = whisperx.align(result["segments"], align_model, align_metadata, audio, device)

    words = []
    for seg in result.get("segments") or []:
        for w in seg.get("words") or []:
            word = w.get("word")
            start = w.get("start")
            end = w.get("end")
            if word is None or start is None or end is None:
                continue
            words.append({"word": str(word), "start": float(start), "end": float(end)})

    out_dir = os.path.dirname(out_words_json)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    try:
        with open(out_words_json, "w", encoding="utf-8") as f:
            json.dump(words, f, ensure_ascii=False, indent=2)
    except OSError as e:
        print(f"Error: Cannot write {out_words_json}: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Written {len(words)} words to {out_words_json}", file=sys.stderr)


if __name__ == "__main__":
    main()
