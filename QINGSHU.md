# 青书 Qingshu

Qingshu 是 [Plektra](https://github.com/pkb-rocks/plektra)（Pastiera 的后续项目）的分支，为 Unihertz Titan 2 实体键盘加入中文拼音输入。
拼音引擎来自 [青简 Qingjian](https://github.com/qingjian-team/qingjian)，直接使用其官方 Android 版的预编译引擎与词库。

两个项目都是 GPL-3.0（青简为 GPL-3.0-or-later），本分支也以 GPL-3.0 发布。Qingshu 不是 Plektra 或青简的官方版本。

## 编译

需要 JDK 17 以上与 Android SDK（platform 36），**不需要** Rust 或 NDK。

```sh
scripts/fetch-qingjian-engine.sh            # 下载青简 v0.3.2，取出 libqingjian_android.so 与 dict.qj（约 15 MB）
# scripts/fetch-qingjian-engine.sh --with-lm  # 另带整句语言模型 lm.qj（约 45 MB）
./gradlew assembleStableDebug
adb install -r app/build/outputs/apk/stable/debug/*.apk
```

引擎与词库不进 git（见 `.gitignore`），每个新 clone 都要先跑一次脚本。只有 arm64 引擎，所以只能在 arm64 手机上用中文。

App ID 是 `io.github.zhangroley76.qingshu`，可以和 Plektra 同时安装。

## 启用中文

1. 系统设置 → 语言和输入法 → 屏幕键盘 → 青书 Qingshu → 语言，勾选「中文 (拼音)」。
2. 打字时用 **Shift+Space** 在中文与上一个语言之间切换；键盘栏的语言按钮、Alt+Space 也可以。

## 按键

| 按键 | 组字中 | 未组字 |
|---|---|---|
| 字母 | 加进拼音 | 开始组字 |
| Shift+字母 | 当作小写加进拼音 | 输入英文大写 |
| 空格 | 上屏高亮候选（预设第 1 个），选了一段会接着组剩下的 | 空格 |
| Alt+W E R / S D F / X C V | 选本页第 1–9 个候选 | 数字 |
| 回车 | 上屏敲的字母 | 回车 |
| 退格 | 删一个字母（刚选的词会退回拼音） | 退格 |
| Back | 取消组字 | 原本功能 |
| 方向键左右 / 在键盘栏左右滑 | 移动高亮 | — |
| 方向键上下 / 键盘栏 ‹ › | 翻页 | — |
| Alt+标点 | 先上屏高亮候选，再输入全角标点 | 全角标点（数字后的 . , : 保持半角） |
| SYM | 先上屏，再开符号页 | 第一页是中文标点 |

点键盘栏上的候选也能直接选。双击 Alt 锁定数字时，组字中按这些键同样是选字。

装了 Shizuku 并开启 Plektra 的触控板手势时：在实体键盘表面左右滑可移动高亮，往上滑上屏高亮候选。

密码、网址、电邮栏位不走拼音。

## 已知限制（第一版）

- 尚未在实机上编译与测试，按键与手势行为需要在 Titan 2 上确认。
- Titan 2 系统设置里的「双击空格输入句号」和空格快捷功能可能拦截空格，请先关掉。
- 实体键盘表面滑动移动高亮：系统的「光标助手」若送出方向键事件即可直接用；否则需要 Shizuku。
- Numen 的英文纠错尚未移植。
