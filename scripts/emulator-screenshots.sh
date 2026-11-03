#!/usr/bin/env bash
# Screenshots for the README, taken on the emulator after scripts/emulator-pinyin-test.sh (the
# APK is installed and Qingshu is the keyboard). The screen is set to the Titan 2 Elite's size.
# Usage: scripts/emulator-screenshots.sh out-dir
set -uo pipefail
OUT="$1"
PKG=io.github.zhangroley76.qingshu
ACT="$PKG/rocks.pkb.plektra.QingshuTestActivity"
mkdir -p "$OUT"
sh() { timeout 30 adb shell "$@"; }
key() { sh input keyevent "$@"; sleep 0.5; }
combo() { sh input keycombination "$@"; sleep 0.8; }
text() { sh input text "$1"; sleep 1; }
shot() { sleep 1.2; timeout 30 adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
host() { sh am start -f 0x20000000 -n "$ACT" "$@" >/dev/null; sleep 1.5; }
mode_is() { timeout 30 adb logcat -d -s PlektraInputMethod:I | grep 'toggled Chinese' | tail -1 | grep -q "mode $1"; }
to_mode() { # cycle Shift+Space until the wanted mode
  for _ in 1 2 3 4 5; do mode_is "$1" && return; combo KEYCODE_SHIFT_LEFT KEYCODE_SPACE; sleep 0.8; done
}

sh wm size 1076x1200
sh wm density 420
sleep 2
host --es languages ko,ja --es traditional off --es corner off --es fuzzy none --ez clear true
key KEYCODE_BACK

# 1. Pinyin with candidates
to_mode zh
host --ez clear true --es hint "Message"
text woxiangquchifan
shot 01-pinyin-candidates
key KEYCODE_BACK
# 2. Association after the pick
host --ez clear true
text nihao
key KEYCODE_SPACE
shot 02-association
key KEYCODE_BACK

# 3. Traditional output
host --ez clear true --es traditional taiwan
text taiwanderen
shot 03-traditional
key KEYCODE_SPACE
key KEYCODE_BACK
host --es traditional off

# 4. English among pinyin candidates
host --ez clear true
text meeting
shot 04-mixed-english
key KEYCODE_BACK

# 5. Korean
to_mode ko
host --ez clear true
text dkssudgktpdy
shot 05-korean
key KEYCODE_SPACE

# 6. Japanese
to_mode ja
host --ez clear true
text nihongo
shot 06-japanese
key KEYCODE_SPACE

# 7. English autocorrect
to_mode en
host --ez clear true
text "Thsi%sis%sa%stest"
key KEYCODE_SPACE
shot 07-english-autocorrect

# 8. Shortcut help
host --ez clear true
sh input text "'/?'"
shot 08-shortcut-help
key KEYCODE_ESCAPE

# 9-10. Settings
host --ez clear true --es open chinese_settings
shot 09-chinese-settings
sh input swipe 540 900 540 300 400
shot 10-chinese-settings-more
key KEYCODE_BACK
host --es open phrases
shot 11-phrases
key KEYCODE_BACK

sh wm size reset
sh wm density reset
ls -la "$OUT"
