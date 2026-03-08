# align

Java 21 / Maven tool that matches WhisperX word timestamps to Russian paragraphs
and writes a cues JSON file for the web reader.

## Prerequisites

Run the transcribe tool first to produce the words JSON for each segment:
  tools/transcribe/transcribe.py

## Usage

  java -jar align.jar <dataDir> <bookId> <segmentId>

Example:
  java -jar align.jar ../../data pushkin_kd s001

## Input

  <dataDir>/<bookId>/words/<segmentId>.json     (from transcribe tool)
  <dataDir>/<bookId>/ru.json
  <dataDir>/<bookId>/audio_index.json

## Output

  <dataDir>/<bookId>/cues/<segmentId>.json

## Build

  mvn package
