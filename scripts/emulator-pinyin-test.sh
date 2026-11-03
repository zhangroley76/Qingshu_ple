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
key() { timeout 30 adb shell input keyevent "$@"; sleep 0.4; }
combo() { timeout 30 adb shell input keycombination "$@"; sleep 0.6; }
type_text() { timeout 30 adb shell input text "$1"; sleep 0.8; }
field_text() { timeout 30 adb logcat -d -s QingshuTest:I | grep -o 'text=\[.*\]' | tail -1; }

checks=()
check() { # name, expected substring of the field text
  local got; got="$(field_text)"
  if [[ "$got" == *"$2"* ]]; then checks+=("PASS | $1 | $got"); else checks+=("FAIL | $1 | expected *$2* got $got"); fi
}

check_absent() { # name, substring that must not appear
  local got; got="$(field_text)"
  if [[ "$got" != *"$2"* ]]; then checks+=("PASS | $1 | $got"); else checks+=("FAIL | $1 | unexpected *$2* in $got"); fi
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

log "association: Alt+W picks the first next word after 你好"
adb exec-out screencap -p > "$OUT/association.png"
combo KEYCODE_ALT_LEFT KEYCODE_W
check "association Alt+W" "你好的"

log "association: Space picks the next one instead of typing a space"
key KEYCODE_SPACE
check_absent "Space picks association" "的 "

log "long-held Alt chords: Alt+W held 800 ms twice in a row both select (no hold layer)"
type_text dajia
adb shell input keycombination -t 800 KEYCODE_ALT_LEFT KEYCODE_W 2>/dev/null || adb shell input keycombination KEYCODE_ALT_LEFT KEYCODE_W
sleep 0.8
check "held Alt+W picks 大家" "大家"
adb shell input keycombination -t 800 KEYCODE_ALT_LEFT KEYCODE_W 2>/dev/null || adb shell input keycombination KEYCODE_ALT_LEFT KEYCODE_W
sleep 0.8
check_absent "second held Alt+W picks an association, not 1" "大家1"
key KEYCODE_BACK

log "Back dismisses associations, then Space types a space"
key KEYCODE_BACK
key KEYCODE_SPACE
check "Space after Back" " "

log "shijie, Alt+P turns to the next page, Alt+W picks its first candidate"
type_text shijie
combo KEYCODE_ALT_LEFT KEYCODE_P
adb exec-out screencap -p > "$OUT/page2.png"
combo KEYCODE_ALT_LEFT KEYCODE_W
check_absent "Alt+P paging (not the first candidate 世界)" "世界"

log "woxiangquchifan, Alt+S picks the 4th (我想去), then Space"
type_text woxiangquchifan
adb exec-out screencap -p > "$OUT/sentence.png"
combo KEYCODE_ALT_LEFT KEYCODE_S
key KEYCODE_SPACE
check "partial pick then Space" "吃饭"

log "custom phrase: yx (added by the test activity) is offered; pick it"
key KEYCODE_BACK
type_text yx
adb exec-out screencap -p > "$OUT/phrase.png"
cands=$(timeout 30 adb logcat -d -s QingshuPinyin:I | grep "key=KEYCODE_X" | tail -1 | grep -o 'candidates=.*')
n=$(echo "$cands" | tr ',' '\n' | grep -n 'test@example.com' | head -1 | cut -d: -f1)
if [ -n "$n" ]; then
  checks+=("PASS | phrase yx offered as candidate $n | $cands")
  combo KEYCODE_ALT_LEFT "$(echo W E R S D F X C V | cut -d' ' -f"$n" | sed 's/^/KEYCODE_/')"
else
  checks+=("FAIL | phrase yx offered | $cands")
  key KEYCODE_SPACE
fi
check "phrase committed" "test@example.com"
key KEYCODE_BACK

log "traditional output (Taiwan): shuru -> 輸入"
traditional() { timeout 30 adb shell am start -f 0x20000000 -n "$PKG/rocks.pkb.plektra.QingshuTestActivity" --es traditional "$1" >/dev/null; sleep 1.5; }
traditional taiwan
type_text shuru
key KEYCODE_SPACE
check "traditional 輸入" "輸入"
key KEYCODE_BACK
traditional off
type_text shuru
key KEYCODE_SPACE
check "back to simplified 输入" "輸入输入"
key KEYCODE_BACK

log "zhongwen + Enter commits letters"
type_text zhongwen
key KEYCODE_ENTER
check "Enter commits letters" "zhongwen"

log "shuru + Backspace + Space"
type_text shuru
key KEYCODE_DEL
key KEYCODE_SPACE
check "Backspace while composing (shur -> 输入)" "输入"

log "Alt+N full-width comma"
combo KEYCODE_ALT_LEFT KEYCODE_N
check "Alt+N comma" "，"

log "Shift+Space -> English"
combo KEYCODE_SHIFT_LEFT KEYCODE_SPACE
type_text abc
check "English after toggle" "abc"

log "Fn tapped once, then S (Plektra nav mapping) moves the cursor left"
key KEYCODE_CTRL_LEFT
key KEYCODE_S
type_text z
check "Fn tap + S is the left arrow" "abzc"

log "Titan Fn (key code 403) held + S is the left arrow (test activity turns Ctrl-hold navigation on)"
combo 403 KEYCODE_S
type_text y
check "Fn(403)+S moves left" "abzyc"

log "English autocorrect (Numen): helo -> hello, teh -> the, Backspace undoes"
key KEYCODE_MOVE_END
type_text "%shelo"
key KEYCODE_SPACE
check "autocorrect helo -> hello" "hello "
type_text "teh"
key KEYCODE_SPACE
check "autocorrect teh -> the" "hello the "
key KEYCODE_DEL
check "Backspace undoes the fix" "hello teh"

log "shortcut help: typing /? removes it and opens the help window"
help_open() { timeout 30 adb shell dumpsys activity activities | grep -q 'QingshuShortcutsActivity'; }
# Quoted for the device shell, where a bare ? is a glob.
timeout 30 adb shell input text "'/?'"
sleep 1.5
if help_open; then checks+=("PASS | /? opens shortcut help | "); else checks+=("FAIL | /? opens shortcut help | not on screen"); fi
adb exec-out screencap -p > "$OUT/help.png"
check_absent "/? removed from the text" "/?"
key KEYCODE_ESCAPE
sleep 1

log "learned Chinese words are saved (written when Chinese is switched off or the field closes)"
key KEYCODE_HOME
sleep 2
learned=$(timeout 30 adb shell run-as $PKG stat -c %s files/qingjian/user/user.tsv 2>/dev/null | tr -d '\r ')
if [ "${learned:-0}" -gt 0 ]; then
  checks+=("PASS | learned words saved | user.tsv is $learned bytes")
else
  checks+=("FAIL | learned words saved | user.tsv missing or empty: $(adb shell run-as $PKG ls -la files/qingjian/user 2>&1 | tr '\n' ' ')")
fi
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
