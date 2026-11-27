#!/usr/bin/env bash
# Regression check for the aligner on real book data.
#
# Copies ru.json, audio_index.json and words/ from <dataDir>/<bookId> into a temp directory, runs the
# built align jar on every segment there, and compares the produced cues byte for byte with the
# existing <dataDir>/<bookId>/cues/. Nothing under <dataDir> is modified (it is only read), and the
# temp directory is removed at the end.
#
# Usage: tools/align/scripts/golden-check.sh <dataDir> <bookId>
#   e.g. tools/align/scripts/golden-check.sh data pushkin_kd
# Build the jar first: (cd tools/align && mvn -q clean package)
#
# Run this before and after a refactor of tools/align. It needs the real data, so it only works on the
# machine that has it. Never commit its temp output (it holds timestamps derived from the audio).
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <dataDir> <bookId>" >&2
  exit 2
fi

data_dir=$(cd "$1" && pwd)
book_id=$2
src="$data_dir/$book_id"
jar="$(cd "$(dirname "$0")/.." && pwd)/target/align-0.1.0.jar"

[ -f "$jar" ] || { echo "Jar not found: $jar (run mvn package in tools/align)" >&2; exit 2; }
[ -d "$src/words" ] || { echo "No words/ directory in $src" >&2; exit 2; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/$book_id/cues"
cp "$src/ru.json" "$src/audio_index.json" "$tmp/$book_id/"
cp -r "$src/words" "$tmp/$book_id/words"

same=0
diff_count=0
for words_file in "$src"/words/s*.json; do
  seg=$(basename "$words_file" .json)
  java -jar "$jar" "$tmp" "$book_id" "$seg" 2>/dev/null
  if cmp -s "$tmp/$book_id/cues/$seg.json" "$src/cues/$seg.json"; then
    same=$((same + 1))
  else
    diff_count=$((diff_count + 1))
    echo "DIFFERENT: $seg"
  fi
done

echo "identical: $same, different: $diff_count"
[ "$diff_count" -eq 0 ]
