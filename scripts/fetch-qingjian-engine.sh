#!/usr/bin/env bash
# Copies Qingjian's prebuilt pinyin engine and dictionary into the app.
#
# Qingshu does not build the Rust engine itself: it takes libqingjian_android.so (arm64) and the
# main dictionary from an official Qingjian Android release, so no Rust or NDK toolchain is needed.
# Qingjian is GPL-3.0-or-later: https://github.com/qingjian-team/qingjian
#
# Usage: scripts/fetch-qingjian-engine.sh [--version v0.3.2] [--with-lm] [--apk path/to/qingjian.apk]
#   --with-lm   also bundle the sentence language model (lm.qj, ~45 MB) for better whole-sentence picks
#   --apk       use an APK you already downloaded instead of fetching it
set -euo pipefail

VERSION="v0.3.2"
WITH_LM=0
APK=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --with-lm) WITH_LM=1; shift ;;
    --apk) APK="$2"; shift 2 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSET_DIR="$ROOT/app/src/main/assets/qingjian"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ -z "$APK" ]]; then
  APK="$WORK/qingjian.apk"
  URL="https://github.com/qingjian-team/qingjian/releases/download/$VERSION/qingjian-${VERSION#v}-android-arm64.apk"
  echo "Downloading $URL"
  curl -fL --retry 3 -o "$APK" "$URL"
fi

mkdir -p "$WORK/x"
# english.tsv: English words among pinyin candidates (中英混打, ~2 MB).
FILES=(lib/arm64-v8a/libqingjian_android.so assets/data/dict.qj assets/data/english.tsv)
[[ $WITH_LM -eq 1 ]] && FILES+=(assets/data/lm.qj)
# Domain dictionaries (idioms, places, IT, ...) and the large general vocabulary, ~76 MB.
FILES+=('assets/data/dicts/*')
unzip -q -o "$APK" "${FILES[@]}" -d "$WORK/x"

mkdir -p "$LIB_DIR" "$ASSET_DIR"
install -m 644 "$WORK/x/lib/arm64-v8a/libqingjian_android.so" "$LIB_DIR/"
rm -f "$ASSET_DIR"/*.qj "$ASSET_DIR"/*.tsv
install -m 644 "$WORK/x/assets/data/dict.qj" "$WORK/x/assets/data/english.tsv" "$ASSET_DIR/"
[[ $WITH_LM -eq 1 ]] && install -m 644 "$WORK/x/assets/data/lm.qj" "$ASSET_DIR/"
rm -rf "$ASSET_DIR/dicts" && mkdir -p "$ASSET_DIR/dicts"
install -m 644 "$WORK"/x/assets/data/dicts/*.qj "$ASSET_DIR/dicts/"

echo "Installed from Qingjian $VERSION:"
(cd "$ROOT" && sha256sum "app/src/main/jniLibs/arm64-v8a/libqingjian_android.so" app/src/main/assets/qingjian/*.qj app/src/main/assets/qingjian/*.tsv)
