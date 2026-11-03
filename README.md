# 青书 Qingshu

**为 Unihertz Titan 2 实体键盘打造的中文拼音输入法**

Qingshu 是 [Plektra](https://github.com/pkb-rocks/plektra)（Pastiera 的后续项目）的分支。它保留 Plektra 完整的实体键盘功能，例如修饰键、SYM 符号页、导航模式和文字片段；在此基础上，加入了用实体键盘就能流畅选字的中文拼音输入。拼音引擎来自 [青简 Qingjian](https://github.com/qingjian-team/qingjian)，词库与语言模型全部离线运作。

*English: Qingshu is a fork of Plektra that adds offline Chinese pinyin input, designed for physical-keyboard phones such as the Unihertz Titan 2. The pinyin engine is Qingjian.*

[下载 APK](https://github.com/zhangroley76/Qingshu_ple/releases/download/qingshu-latest/qingshu-debug.apk) · [完整按键说明](QINGSHU.md) · [模拟器测试结果](https://github.com/zhangroley76/Qingshu_ple/releases/tag/qingshu-emulator-test)

## 为什么做这个

在实体键盘手机上，常见的中文输入法（例如 fcitx 的 Android 版）通常只能用空格上屏第一个候选，选其他字就得伸手去点屏幕。Qingshu 让选字、翻页、联想都能在键盘上完成，手不用离开按键。

## 功能

| 功能 | 说明 |
| --- | --- |
| 中英文切换 | Shift+空格，或点键盘栏的语言按钮（中文时显示「中」） |
| 键盘选字 | Alt + W E R / S D F / X C V 选第 1–9 个候选，对应 Titan 键盘上的数字位置；空格上屏高亮的候选 |
| 连续选字 | 可以只选一段，剩下的拼音继续组字；按住 Alt 能连续选，不会进入长按符号层 |
| 翻页与高亮 | Alt+P 翻页（翻到最后一页回第一页）；方向键或在键盘栏左右滑可移动高亮；点 ‹ › 翻页 |
| 离线联想 | 上屏后显示常接在后面的词，用空格、Alt+数字或点选接下去 |
| 中文标点 | Alt+标点输入全角标点（数字后的 . , : 保持半角）；SYM 第一页是中文标点 |
| Shift/Alt 对调 | Titan 的 Alt 位置不好按数字，可以在设置 → Keyboard → Modifiers 对调 |
| 不干扰英文 | 中文模式下首字母不会被自动大写；密码、网址、电邮栏位不走拼音；英文模式维持 Plektra 原本的行为 |
| 调试报告 | 从 App 内分享报告时附上按键日志，方便回报问题 |

Plektra 原有的功能都还在，包括导航模式（Fn+E S D F 方向键）、SYM 符号页、剪贴板历史、文字展开和键盘栏主题。详见 [Plektra 的说明](https://github.com/pkb-rocks/plektra)。

## 安装

1. 下载 [qingshu-debug.apk](https://github.com/zhangroley76/Qingshu_ple/releases/download/qingshu-latest/qingshu-debug.apk) 并安装。
2. 在系统设置里启用「青书 Qingshu」输入法，并设为当前输入法。
3. 打字时按 Shift+空格切换到中文。

更新时直接覆盖安装，不要先卸载，否则要重新启用输入法。App ID 是 `io.github.zhangroley76.qingshu`，可以和 Plektra 同时安装。APK 约 60 MB，主要是词库和语言模型。

## 当前状态

这是测试版，用 debug 签名发布。

- 中文切换、空格选字、Shift/Alt 对调、候选栏已经在 Titan 2 Elite 实机上确认。
- 每次推送都会用 GitHub Actions 编译，并在 Android 模拟器上跑自动化按键测试，覆盖组字、选字、联想、翻页和标点。
- 点选候选、SYM 中文标点、键盘表面滑动这几项还要在实机上确认。

**计划中**：移植 [Numen](https://github.com/jlo-aug/numen) 的英文自动纠错。

## 编译

每次推送 `pinyin` 分支，GitHub Actions 会自动编译，并把 APK 发布到 Releases 的 `qingshu-latest`。

本地编译需要 JDK 17 以上和 Android SDK（platform 36）：

```sh
scripts/fetch-qingjian-engine.sh --with-lm   # 下载青简 v0.3.2 的词库与语言模型
ANDROID_NDK_HOME=/path/to/ndk scripts/build-qingjian-engine.sh arm64-v8a   # 从源码编译引擎（含联想）
./gradlew assembleStableDebug
```

Qingshu 对青简源码的修改放在 [`third_party/qingjian/`](third_party/qingjian/)，主要是离线联想的部分。更多说明见 [QINGSHU.md](QINGSHU.md)。

## 致谢与授权

- [Plektra](https://github.com/pkb-rocks/plektra)：Patrick Zauner；前身是 Andrea Palumbo（PalSoftware）开发的 Pastiera
- [青简 Qingjian](https://github.com/qingjian-team/qingjian)：拼音引擎、词库与语言模型
- [Numen](https://github.com/jlo-aug/numen)：英文纠错的参考来源（计划中）

Plektra 和青简都使用 GPL-3.0 授权（青简是 GPL-3.0-or-later），Qingshu 同样以 [GPL-3.0](LICENSE) 发布。Qingshu 不是 Plektra 或青简的官方版本，请不要把这里的问题回报给上游项目。
