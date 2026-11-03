#!/usr/bin/env bash
# Downloads SKK-JISYO.L (GPL-2.0-or-later, https://github.com/skk-dev/dict) and turns it into
# app/src/main/assets/japanese/skk-jisyo.txt for Qingshu's Japanese input: UTF-8, one line per
# reading ("reading<TAB>cand1/cand2/"), annotations removed, sorted by reading (code points).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/assets/japanese"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
curl -fsSL --retry 3 -o "$WORK/SKK-JISYO.L" https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.L
mkdir -p "$OUT"
python3 - "$WORK/SKK-JISYO.L" "$OUT/skk-jisyo.txt" <<'PY'
import sys, re
raw = open(sys.argv[1], 'rb').read().decode('euc_jis_2004', errors='replace')
entries = {}
for line in raw.splitlines():
    if not line or line.startswith(';;'):
        continue
    key, _, rest = line.partition(' /')
    key = key.strip()
    if not key or not rest:
        continue
    cands = []
    for c in rest.rstrip('/').split('/'):
        c = c.split(';', 1)[0]          # annotation after ';'
        if not c or c.startswith('(') or c.startswith('['):   # (concat ...) forms, okuri blocks
            continue
        if c not in cands:
            cands.append(c)
    if cands:
        entries.setdefault(key, [])
        entries[key] += [c for c in cands if c not in entries[key]]
with open(sys.argv[2], 'w', encoding='utf-8') as out:
    for key in sorted(entries):
        out.write(key + '\t' + '/'.join(entries[key]) + '\n')
print(len(entries), 'readings')
PY
ls -la "$OUT/skk-jisyo.txt"
