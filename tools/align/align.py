#!/usr/bin/env python3
"""Minimal stub aligner: generates paragraph-level cues evenly spaced over audio."""

import json
import os
import subprocess
import sys


def main():
    usage = (
        "Usage: python align.py <bookId> <segmentId> <audioFile> <ruJsonFile> <outCuesJson>\n"
        "Example: python align.py pushkin_kd s001 audio/s001.mp3 data/pushkin_kd/ru.json data/pushkin_kd/cues/s001.json"
    )

    if len(sys.argv) != 6:
        print(usage, file=sys.stderr)
        sys.exit(1)

    book_id, segment_id, audio_file, ru_json_file, out_cues_json = sys.argv[1:6]

    # Read ru.json
    try:
        with open(ru_json_file, encoding="utf-8") as f:
            ru_data = json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"Error reading {ru_json_file}: {e}", file=sys.stderr)
        sys.exit(1)

    paragraphs = ru_data.get("paragraphs", [])
    if not paragraphs:
        print("No paragraphs in ru.json", file=sys.stderr)
        sys.exit(1)

    # Get audio duration via ffprobe
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audio_file,
            ],
            capture_output=True,
            text=True,
            check=True,
        )
        duration = float(result.stdout.strip())
    except FileNotFoundError:
        print("Error: ffprobe not found. Install ffmpeg.", file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"Error running ffprobe: {e.stderr or e}", file=sys.stderr)
        sys.exit(1)
    except ValueError as e:
        print(f"Error parsing audio duration: {e}", file=sys.stderr)
        sys.exit(1)

    # Generate cues evenly distributed
    n = len(paragraphs)
    cues = []
    for i, p in enumerate(paragraphs):
        start = round(i * (duration / n), 3)
        end = round((i + 1) * (duration / n), 3)
        cues.append({
            "paragraphId": p.get("id", f"ru-{i:06d}"),
            "start": start,
            "end": end,
        })

    out_data = {
        "bookId": book_id,
        "audioFile": os.path.basename(audio_file),
        "cues": cues,
    }

    # Ensure output parent directory exists
    out_dir = os.path.dirname(out_cues_json)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    try:
        with open(out_cues_json, "w", encoding="utf-8") as f:
            json.dump(out_data, f, ensure_ascii=False, indent=2)
    except OSError as e:
        print(f"Error writing {out_cues_json}: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
