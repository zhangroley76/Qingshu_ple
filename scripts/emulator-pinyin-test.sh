#!/usr/bin/env bash
# Drives Qingshu's pinyin input on an emulator with injected key events and writes a report.
#
# Usage: scripts/emulator-pinyin-test.sh path/to/qingshu-debug.apk report.md
# Needs a running emulator (adb). The pinyin engine is arm64-only, so an x86_64 emulator relies
# on its ARM translation; the report says whether the engine loaded.
set -uo pipefail

APK="$1"
REPORT="$2"
PKG=io.github.zhangroley76.qingshu
IME="$PKG/rocks.pkb.plektra.inputmethod.PhysicalKeyboardInputMethodService"
OUT="$(dirname "$REPORT")"

log() { echo "### $*"; }
key() { adb shell input keyevent "$@"; sleep 0.4; }
combo() { adb shell input keycombination "$@"; sleep 0.6; }
type_text() { adb shell input text "$1"; sleep 0.8; }
field_text() { adb logcat -d -s QingshuTest:I | grep -o 'text=\[.*\]' | tail -1; }

checks=()
check() { # name, expected substring of the field text
  local got; got="$(field_text)"
  if [[ "$got" == *"$2"* ]]; then checks+=("PASS | $1 | $got"); else checks+=("FAIL | $1 | expected *$2* got $got"); fi
}

log "install"
adb install -r -g "$APK" || exit 1
adb shell settings put secure show_ime_with_hard_keyboard 1
# The IME registers asynchronously after install; retry until Android reports it as current.
for _ in $(seq 1 30); do
  adb shell ime enable "$IME" >/dev/null 2>&1
  adb shell ime set "$IME" >/dev/null 2>&1
  [[ "$(adb shell settings get secure default_input_method | tr -d '\r')" == "$IME" ]] && break
  sleep 2
done
echo "default_input_method=$(adb shell settings get secure default_input_method)"

adb logcat -c
adb shell am start -W -n "$PKG/rocks.pkb.plektra.QingshuTestActivity"
sleep 6
adb shell ime list -s
adb shell settings get secure default_input_method

log "wait for engine"
for _ in $(seq 1 30); do
  adb logcat -d | grep -q -E 'Engine ready|Engine failed' && break
  sleep 1
done

log "Shift+Space -> Chinese"
combo KEYCODE_SHIFT_LEFT KEYCODE_SPACE
sleep 2
for _ in $(seq 1 30); do
  adb logcat -d | grep -q -E 'Engine ready|Engine failed' && break
  sleep 1
done

log "nihao + space"
type_text nihao
adb exec-out screencap -p > "$OUT/composing.png"
key KEYCODE_SPACE
check "nihao + Space" "你好"

log "woxiangquchifan, Alt+S picks the 4th (我想去), then Space"
type_text woxiangquchifan
adb exec-out screencap -p > "$OUT/sentence.png"
combo KEYCODE_ALT_LEFT KEYCODE_S
key KEYCODE_SPACE
check "partial pick then Space" "吃饭"

log "zhongwen + Enter commits letters"
type_text zhongwen
key KEYCODE_ENTER
check "Enter commits letters" "zhongwen"

log "shuru + Backspace + Space"
type_text shuru
key KEYCODE_DEL
key KEYCODE_SPACE
check "Backspace while composing" "书"

log "Alt+N full-width comma"
combo KEYCODE_ALT_LEFT KEYCODE_N
check "Alt+N comma" "，"

log "Shift+Space -> English"
combo KEYCODE_SHIFT_LEFT KEYCODE_SPACE
type_text abc
check "English after toggle" "abc"
adb exec-out screencap -p > "$OUT/final.png"

adb logcat -d -v time > "$OUT/logcat.txt"

{
  echo "## Emulator pinyin test"
  echo
  echo "IME: $(adb shell settings get secure default_input_method | tr -d '\r')"
  echo
  echo '```'
  printf '%s\n' "${checks[@]}"
  echo '```'
  echo
  echo "### Field text history"
  echo '```'
  grep -o 'QingshuTest.*' "$OUT/logcat.txt" | tail -n 60
  echo '```'
  echo "### Pinyin / engine log"
  echo '```'
  grep -E 'QingshuPinyin|Pinyin:|qingjian|UnsatisfiedLink|FATAL|AndroidRuntime' "$OUT/logcat.txt" | tail -n 150 | cut -c1-400
  echo '```'
} > "$REPORT"
cat "$REPORT"
