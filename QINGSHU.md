# 青书 Qingshu

Qingshu 是 [Plektra](https://github.com/pkb-rocks/plektra)（Pastiera 的后续项目）的分支，为 Unihertz Titan 2 实体键盘加入中文拼音输入。
拼音引擎来自 [青简 Qingjian](https://github.com/qingjian-team/qingjian)：词库与语言模型取自其官方 Android 版，引擎从其源码编译并加上离线联想（见 `third_party/qingjian/`）。

两个项目都是 GPL-3.0（青简为 GPL-3.0-or-later），本分支也以 GPL-3.0 发布。Qingshu 不是 Plektra 或青简的官方版本。

## 编译

GitHub Actions 会在每次推送 `pinyin` 分支后自动编译，APK 发布在 Releases 的 `qingshu-latest`，模拟器测试结果在 `qingshu-emulator-test`。

本地编译需要 JDK 17 以上与 Android SDK（platform 36）：

```sh
scripts/fetch-qingjian-engine.sh --with-lm   # 青简 v0.3.2 的词库 dict.qj 与语言模型 lm.qj（约 60 MB）
# 要离线联想，再从源码编译引擎（需要 rustup、cargo-ndk 与 NDK）：
ANDROID_NDK_HOME=/path/to/ndk scripts/build-qingjian-engine.sh arm64-v8a
./gradlew assembleStableDebug
```

只跑第一行也能用，但官方预编译的引擎没有联想。Qingshu 对青简源码的修改放在 `third_party/qingjian/*.patch`。

引擎与词库不进 git（见 `.gitignore`）。App ID 是 `io.github.zhangroley76.qingshu`，可以和 Plektra 同时安装。

## 启用中文

打字时按 **Shift+Space**，或点键盘栏的语言按钮，在中文与目前的语言之间切换（按钮显示「中」表示中文）。按住 Ctrl/Fn 点语言按钮仍是轮换其他语言。

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
| Alt+P / 方向键上下 / 键盘栏 ‹ › | 翻页（Alt+P 翻到最后一页后回到第一页） | — |
| Alt+标点 | 先上屏高亮候选，再输入全角标点 | 全角标点（数字后的 . , : 保持半角） |
| SYM | 先上屏，再开符号页 | 第一页是中文标点 |

点键盘栏上的候选也能直接选。

**常用语**：设置 → 中文常用语。有代码的常用语，在中文模式打它的代码就会出现在候选（`yx` → 邮箱）。资料存在青简的用户目录（`files/qingjian/user/phrases.json`）。

**快捷键说明**：任何输入框打 `/?`（中文下 `/？` 也行）就弹出说明，这两个字会被删掉。

**方向键**（Plektra 原有）：E S D F 是上 左 下 右（J K L 也是左 下 右），A 全选，W/R 往左/右扩大选取。用法：先点一下 Fn 再按键（单次），或连点两下 Fn 锁定导航模式。预设「按住 Fn」会把组合键交给 App（例如 Ctrl+A 全选、Ctrl+S 存档）；要按住 Fn 就当方向键，到设置 → Nav Mode 打开「Ctrl-hold navigation」。Titan 2 Elite 的 Fn 键送出厂商键码 403，Qingshu 把它当成 Ctrl。组字中按这些键会先把高亮候选上屏。

用按住的 Alt 选字（中文模式）不会在放开时进入长按的符号层或锁定，可以按住连续选。

**联想**：上屏后键盘栏显示常接在后面的词。空格上屏高亮的联想（可连续按），Alt+W…V（1–9）或点选上屏指定的词，选完会继续联想。Back 关掉联想（之后空格打空格），打字母或其他键时联想自动消失。

双击 Alt 锁定数字时，组字中按这些键同样是选字；未组字时（包括显示联想时）照常输入数字。

装了 Shizuku 并开启 Plektra 的触控板手势时：在实体键盘表面左右滑可移动高亮，往上滑上屏高亮候选。

密码、网址、电邮栏位不走拼音。

## 已知限制（第一版）

- 已在 Titan 2 Elite 实机使用；点选候选、‹ › 翻页、SYM 中文标点、键盘表面滑动尚待实机确认。
- 用户词库是否学习常用词尚未验证。
- Titan 2 系统设置里的「双击空格输入句号」和空格快捷功能可能拦截空格，请先关掉。
- 实体键盘表面滑动移动高亮：系统的「光标助手」若送出方向键事件即可直接用；否则需要 Shizuku。
- 英文自动纠错移植自 Numen（`core/suggestions` 整套换成 Numen 的版本，资料在 `assets/common/dictionaries/en_*`）。纠正后马上按退格可还原；同一个字还原两次会被学起来。设置里的 Auto-replace on space/enter 可以关掉。

## 接下来的计划

依序：

1. ~~中文学习存不下来~~（已修：青简只在 reset 时存档，现在于输入框结束和关掉中文时存）。
2. 繁体输出开关（OpenCC 对照，可选台湾用字）。
3. ~~中文自定义短语~~（已做：设置 → 中文常用语）。
4. 中英混打：拼音候选里同时列出对应的英文单字。
5. 模糊音（z/zh、c/ch、s/sh、n/l、an/ang）与简拼（`nh` → 你好）。
6. ~~Obtainium 自动更新~~（已做：每次编译版本号递增、发布编号版本；签名不变，免得要卸载重装）。

之后再看：在键盘表面滑动选字（需要 Shizuku）、Numen 的打字统计首页、直角引号「」。

有空再做（方便其他非拉丁文字的使用者）：韩文（两套式键位，规则组字，不需词库）、日文（青简已有罗马字→假名与少量常用词；完整的假名→汉字转换要另接引擎，例如 Mozc）。
