#!/usr/bin/env python3
"""Segment-based aligner using WhisperX for real audio/paragraph alignment."""

import json
import os
import sys
import re
from typing import Any, Dict, List


def _die(msg: str) -> "None":
    print(f"Error: {msg}", file=sys.stderr)
    raise SystemExit(1)


def _load_json(path: str) -> dict:
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        _die(f"File not found: {path}")
    except OSError as e:
        _die(f"Cannot read {path}: {e}")
    except json.JSONDecodeError as e:
        _die(f"Invalid JSON in {path}: {e}")


def _normalize(text: str) -> str:
    """
    Lowercase, keep only Unicode letters and spaces, strip digits, collapse spaces.
    Works correctly for Cyrillic using re.UNICODE.
    """
    text = text.lower()
    text = re.sub(r"[^\w\s]", "", text, flags=re.UNICODE)
    text = re.sub(r"\d", "", text, flags=re.UNICODE)
    text = re.sub(r"\s+", " ", text, flags=re.UNICODE)
    return text.strip()


def _flatten_whisperx_words(result: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Flatten WhisperX segments into a single list of word dicts:
    { "word": str, "start": float, "end": float }
    Skip entries missing timing or that normalize to empty.
    """
    flat: List[Dict[str, Any]] = []
    segments = result.get("segments") or []
    for seg in segments:
        words = seg.get("words") or []
        for w in words:
            word = w.get("word")
            start = w.get("start")
            end = w.get("end")
            if word is None or start is None or end is None:
                continue
            norm = _normalize(str(word))
            if not norm:
                continue
            flat.append(
                {
                    "word": str(word),
                    "start": float(start),
                    "end": float(end),
                    "norm": norm,
                }
            )
    return flat


def _run_whisperx(audio_path: str) -> List[Dict[str, Any]]:
    """Run WhisperX on the given audio file and return a flat word list."""
    try:
        import whisperx  # type: ignore[import]
    except ImportError:
        _die(
            "whisperx is not installed. Install it with 'pip install whisperx' "
            "and ensure ffmpeg is available."
        )

    device = "cpu"
    model = whisperx.load_model("medium", device, language="ru", compute_type="int8")
    audio = whisperx.load_audio(audio_path)
    result = model.transcribe(audio, language="ru")
    align_model, align_metadata = whisperx.load_align_model(
        language_code="ru", device=device
    )
    result = whisperx.align(result["segments"], align_model, align_metadata, audio, device)
    return _flatten_whisperx_words(result)


def _align_paragraphs_to_words(
    slice_paragraphs: List[Dict[str, Any]],
    flat_words: List[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    """
    Fuzzy paragraph matcher over the flat word list.

    For each paragraph (in order):
      - Tokenize normalized cyr text.
      - Starting from current search position, try each candidate start within a
        forward window of 50 words.
      - At each candidate, walk paragraph tokens and words together, counting matches
        where normalized(word) == paragraph_token, allowing up to 2 consecutive
        mismatches before abandoning the candidate.
      - Accept the first candidate where matched_count >= 0.5 * len(paragraph_tokens).
      - If accepted, build a cue with paragraphId, start/end from first/last matched
        words, and the words in that span, and advance global search position to just
        after the last matched word.
      - If no candidate accepted, emit no cue for that paragraph and do not advance
        the search position.
    """
    cues: List[Dict[str, Any]] = []
    if not flat_words:
        return cues

    search_pos = 0
    n_words = len(flat_words)
    window_size = 50

    for p in slice_paragraphs:
        pid = p.get("id")
        cyr_text = p.get("cyr")
        if not isinstance(cyr_text, str) or not cyr_text.strip():
            # Fall back to a generic "text" field if present.
            fallback = p.get("text")
            if isinstance(fallback, str) and fallback.strip():
                cyr_text = fallback
            else:
                continue

        para_norm = _normalize(cyr_text)
        if not para_norm:
            continue
        tokens = para_norm.split()
        if not tokens:
            continue

        accepted_first_idx: int | None = None
        accepted_last_idx: int | None = None

        # Search candidate starts within a forward window.
        start_limit = min(search_pos + window_size, n_words)
        for cand_start in range(search_pos, start_limit):
            word_idx = cand_start
            token_idx = 0
            first_idx: int | None = None
            last_idx: int | None = None
            matched_count = 0
            consecutive_mismatches = 0

            while token_idx < len(tokens) and word_idx < n_words:
                word_entry = flat_words[word_idx]
                if word_entry["norm"] == tokens[token_idx]:
                    if first_idx is None:
                        first_idx = word_idx
                    last_idx = word_idx
                    matched_count += 1
                    consecutive_mismatches = 0
                    token_idx += 1
                    word_idx += 1
                else:
                    word_idx += 1
                    consecutive_mismatches += 1
                    if consecutive_mismatches > 2:
                        break

            if matched_count >= max(1, int(0.5 * len(tokens))) and first_idx is not None and last_idx is not None:
                accepted_first_idx = first_idx
                accepted_last_idx = last_idx
                break

        if accepted_first_idx is None or accepted_last_idx is None:
            # No cue for this paragraph; do not advance search_pos.
            continue

        words_span = flat_words[accepted_first_idx : accepted_last_idx + 1]
        cue_words = [
            {
                "text": w["word"],
                "start": w["start"],
                "end": w["end"],
            }
            for w in words_span
        ]

        cues.append(
            {
                "paragraphId": pid,
                "start": words_span[0]["start"],
                "end": words_span[-1]["end"],
                "words": cue_words,
            }
        )

        # Advance global search position.
        search_pos = accepted_last_idx + 1

    return cues


def main() -> None:
    usage = "Usage: python align.py <bookId> <segmentId> <ru.json> <audio_index.json> <outCuesJson>"
    if len(sys.argv) != 6:
        print(usage, file=sys.stderr)
        raise SystemExit(1)

    book_id, segment_id, ru_json_path, audio_index_path, out_cues_json = sys.argv[1:6]

    ru_data = _load_json(ru_json_path)
    paragraphs = ru_data.get("paragraphs")
    if not isinstance(paragraphs, list) or not paragraphs:
        _die("ru.json contains no paragraphs")

    id_to_index: dict[str, int] = {}
    for i, p in enumerate(paragraphs):
        if not isinstance(p, dict):
            _die(f"ru.json paragraph at index {i} is not an object")
        pid = p.get("id")
        if not isinstance(pid, str) or not pid.strip():
            _die(f"ru.json paragraph at index {i} is missing a valid 'id'")
        if pid in id_to_index:
            _die(f"Duplicate paragraph id in ru.json: {pid}")
        id_to_index[pid] = i

    audio_index = _load_json(audio_index_path)
    segments = audio_index.get("segments")
    if not isinstance(segments, list):
        _die("audio_index.json is missing 'segments' array")

    segment = next((s for s in segments if isinstance(s, dict) and s.get("id") == segment_id), None)
    if segment is None:
        _die(f"segmentId not found in audio_index.json: {segment_id}")

    audio_file_rel = segment.get("audioFile")
    start_pid = segment.get("contentStartParagraph")
    end_pid = segment.get("endParagraph")

    if not isinstance(audio_file_rel, str) or not audio_file_rel.strip():
        _die(f"Segment '{segment_id}' is missing a valid 'audioFile'")
    if not isinstance(start_pid, str) or not start_pid.strip():
        _die(f"Segment '{segment_id}' is missing a valid 'contentStartParagraph'")
    if not isinstance(end_pid, str) or not end_pid.strip():
        _die(f"Segment '{segment_id}' is missing a valid 'endParagraph'")

    if start_pid not in id_to_index:
        _die(f"Start paragraph id not found in ru.json: {start_pid}")
    if end_pid not in id_to_index:
        _die(f"End paragraph id not found in ru.json: {end_pid}")

    start_i = id_to_index[start_pid]
    end_i = id_to_index[end_pid]
    if start_i > end_i:
        _die(f"Start paragraph comes after end paragraph: {start_pid} > {end_pid}")

    slice_paragraphs = paragraphs[start_i : end_i + 1]
    if not slice_paragraphs:
        _die("Resolved paragraph slice is empty")

    audio_index_dir = os.path.dirname(os.path.abspath(audio_index_path))
    audio_path = os.path.join(audio_index_dir, audio_file_rel)
    flat_words = _run_whisperx(audio_path)
    cues = _align_paragraphs_to_words(slice_paragraphs, flat_words)

    out_data = {"bookId": book_id, "audioFile": os.path.basename(audio_path), "cues": cues}

    out_dir = os.path.dirname(out_cues_json)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)

    try:
        with open(out_cues_json, "w", encoding="utf-8") as f:
            json.dump(out_data, f, ensure_ascii=False, indent=2)
    except OSError as e:
        _die(f"Cannot write {out_cues_json}: {e}")

    # Diagnostics to stderr.
    total_paragraphs = len(slice_paragraphs)
    matched_paragraphs = len(cues)
    if matched_paragraphs:
        first_pid = cues[0]["paragraphId"]
        last_pid = cues[-1]["paragraphId"]
    else:
        first_pid = "none"
        last_pid = "none"

    print(f"total paragraphs in slice: {total_paragraphs}", file=sys.stderr)
    print(f"paragraphs matched: {matched_paragraphs}", file=sys.stderr)
    print(f"first matched paragraphId: {first_pid}", file=sys.stderr)
    print(f"last matched paragraphId: {last_pid}", file=sys.stderr)


if __name__ == "__main__":
    main()
