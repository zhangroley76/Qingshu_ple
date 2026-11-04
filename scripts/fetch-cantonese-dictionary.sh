#!/usr/bin/env bash
# Downloads the rime-cantonese lexicon (CC BY 4.0, https://github.com/rime/rime-cantonese) and turns
# it into app/src/main/assets/cantonese/jyutping.txt for Qingshu's Cantonese (Jyutping) input.
#
# One line per toneless reading, sorted by reading: "reading<TAB>weight<TAB>word1/word2/...", words
# best first, weight = the first word's frequency (from essay-cantonese.txt). Readings are Jyutping
# without tones or spaces ("nei5 hou2" -> "neihou"). Lines starting with "#" are initials-only
# abbreviations of 2–4 syllable words ("#nh" -> 你好), consulted after full readings.
# Phrases and essay words that the lexicon lists without readings are spelled from their
# characters' main readings.
# Usage: fetch-cantonese-dictionary.sh [local rime-cantonese checkout]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/cantonese"
REV="259f0e48bba840c3a2e0d117539e96937f3d89bc"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
if [ -n "${1:-}" ]; then
  SRC="$1"
else
  SRC="$WORK/rime-cantonese"
  mkdir -p "$SRC"
  for f in jyut6ping3.chars.dict.yaml jyut6ping3.words.dict.yaml jyut6ping3.phrase.dict.yaml \
           jyut6ping3.lettered.dict.yaml essay-cantonese.txt; do
    curl -fsSL --retry 3 -o "$SRC/$f" "https://raw.githubusercontent.com/rime/rime-cantonese/$REV/$f"
  done
fi
mkdir -p "$OUT"
python3 -I - "$SRC" "$OUT/jyutping.txt" <<'PY'
import sys, re, itertools, collections
src, out_path = sys.argv[1], sys.argv[2]

def body(name):
    started = False
    for line in open(f"{src}/{name}", encoding="utf-8"):
        line = line.rstrip("\n")
        if not started:
            started = line == "..."
            continue
        if line and not line.startswith("#"):
            yield line.split("\t")

freq = collections.Counter()
for line in open(f"{src}/essay-cantonese.txt", encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) == 2 and parts[1].isdigit():
        freq[parts[0]] = int(parts[1])

def toneless(reading):
    return re.sub(r"[0-9]", "", reading)

# Characters: readings with their share ("3%" = rare reading; no percent = a main reading).
char_readings = collections.defaultdict(list)   # char -> [(syllable_with_tone, share)]
for f in body("jyut6ping3.chars.dict.yaml"):
    if len(f) < 2 or not f[1]:
        continue
    share = 100.0
    if len(f) >= 3 and f[2].endswith("%"):
        try:
            share = float(f[2][:-1])
        except ValueError:
            pass
    char_readings[f[0]].append((f[1], share))

entries = collections.defaultdict(dict)   # key -> {word: weight}
syllables_of = {}                          # word -> list of toneless syllables (for abbreviations)

def add(word, reading, weight):
    key = toneless(reading).replace(" ", "")
    if not key or not re.fullmatch(r"[a-z]+", key):
        return
    if entries[key].get(word, -1) < weight:
        entries[key][word] = weight
    syl = toneless(reading).split()
    if word not in syllables_of or len(syl) == len(word):
        syllables_of[word] = syl

for ch, readings in char_readings.items():
    base = freq.get(ch, 0)
    for reading, share in readings:
        if share <= 0:
            continue
        add(ch, reading, int(base * share / 100) + 1)

for name in ("jyut6ping3.words.dict.yaml", "jyut6ping3.lettered.dict.yaml"):
    for f in body(name):
        if len(f) >= 2 and f[1]:
            if len(f) >= 3 and f[2].endswith("%") and f[2] in ("0%",):
                continue
            add(f[0], f[1], freq.get(f[0], 0) + 1)

def main_readings(ch):
    rs = char_readings.get(ch)
    if not rs:
        return None
    best = max(s for _, s in rs)
    return [r for r, s in rs if s == best][:2]

spelled = 0
for f in body("jyut6ping3.phrase.dict.yaml"):
    word = f[0]
    if len(f) >= 2 and f[1]:
        add(word, f[1], freq.get(word, 0) + 1)
        continue
    if freq.get(word, 0) == 0 or len(word) > 8:
        continue
    per_char = [main_readings(c) for c in word]
    if any(r is None for r in per_char):
        continue
    for combo in itertools.islice(itertools.product(*per_char), 4):
        add(word, " ".join(combo), freq[word] + 1)
    spelled += 1

# The essay (word frequencies Rime uses to build sentences) also lists everyday words that no
# dictionary file has, such as 你好: spell those from their characters too.
for word, count in freq.items():
    if word in syllables_of or not (2 <= len(word) <= 6):
        continue
    per_char = [main_readings(c) for c in word]
    if any(r is None for r in per_char):
        continue
    for combo in itertools.islice(itertools.product(*per_char), 4):
        add(word, " ".join(combo), count + 1)
    spelled += 1

abbrev = collections.defaultdict(dict)
for key, words in entries.items():
    for word, weight in words.items():
        syl = syllables_of.get(word)
        if syl and 2 <= len(syl) <= 4 and weight > 1:
            akey = "#" + "".join(s[0] for s in syl)
            if abbrev[akey].get(word, -1) < weight:
                abbrev[akey][word] = weight

lines = 0
with open(out_path, "w", encoding="utf-8") as out:
    allkeys = dict(entries)
    allkeys.update(abbrev)
    for key in sorted(allkeys):
        words = sorted(allkeys[key].items(), key=lambda kv: (-kv[1], kv[0]))
        if key.startswith("#"):
            words = words[:60]
        out.write(f"{key}\t{words[0][1]}\t" + "/".join(w for w, _ in words) + "\n")
        lines += 1
print(f"{len(entries)} readings, {len(abbrev)} abbreviations, {spelled} phrases spelled, {lines} lines")
PY
ls -la "$OUT/jyutping.txt"
