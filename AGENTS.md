# Qingshu 青书 — 给 AI 代理的项目说明（AGENTS.md）

> 2026-11-09 建立。与 [CLAUDE.md](CLAUDE.md) 内容一致；**改规则时两份一起改**。

## 这是什么

- 给实体键盘 Android 手机用的输入法，针对 **Unihertz Titan 2 Elite**（作者自己用的手机）。是 [Plektra](https://github.com/pkb-rocks/plektra)（Pastiera 的后续项目）的分支，GPL-3.0。App ID `io.github.zhangroley76.qingshu`，可与 Plektra 同时安装。
- 中文拼音：[青简 Qingjian](https://github.com/qingjian-team/qingjian) 引擎。词库与语言模型取自官方 Android 版，引擎从源码编译并加上离线联想；对青简源码的修改在 `third_party/qingjian/*.patch`（0001 联想、0002 繁体联想）。引擎与词库不进 git。
- 英文自动纠错：移植自 [Numen](https://github.com/jlo-aug/numen)，`core/suggestions` 整套换成 Numen 的版本，资料在 `assets/common/dictionaries/en_*`。
- 可选语言：粤语（粤拼，词库 rime-cantonese，CC BY 4.0）、韩文（两套式）、日文（罗马字 + SKK-JISYO.L）。
- 全部离线处理，没有账号、统计，也不自己检查更新。

## 先读

1. [README.md](README.md)（英文）／[README.zh.md](README.zh.md)
2. [QINGSHU.md](QINGSHU.md)：中文的按键说明、各项功能、**已知限制**、计划
3. [third_party/qingjian/README.md](third_party/qingjian/README.md)：引擎编译与补丁

## 和作者合作

- 作者 roleyzhang。回复与文档用中文，偏好台湾通行的译名；代码注释与提交信息用英文（沿用现有习惯）。
- 直接、先给答案；没查证的要标明，不要说过头。
- **分批做**：一批几项，做完作者在实机（Titan 2 Elite）上试，有问题再改。
- 日文、韩文作者自己不用，做它们是为了帮到其他使用非拉丁文字的人；粤语来自 Reddit r/unihertz 用户的反馈，欢迎粤语使用者意见。
- 作者的个人习惯：空格可以**连续**上屏联想词。

## 分支、编译与发布

- 发布分支是 **`pinyin`**（`main` 与它保持同一个提交）。推送到 `pinyin`、`autocorrect`、`dev/**` 都会触发 GitHub Actions（`qingshu-apk.yml`）编译并跑模拟器测试；`dev/**` 与 `autocorrect` 的产物带 `-dev` 名称。
- **做法**：每一批改动开一个 `dev/<名称>` 分支，推送后让 CI 编译、测试，开 PR 到 `pinyin`，**由作者决定合并**；合并后把 `main` 同步到 `pinyin`。不要直接推送到 `pinyin` 或 `main`。（2026-11-09 起；之前的批次是 dev 分支测试通过后快进到 `pinyin`。）
- 发布：`pinyin` 编译出的 APK 发在 Releases 的 `qingshu-latest`，并发布编号版本（例如 v26.09.76）；模拟器测试报告在 `qingshu-emulator-test`。正式签名、版本号每次递增，用 Obtainium 更新不必重装。
- 作者也在家里的 Arch 上本地编译：步骤见 `QINGSHU.md`「编译」。
- **签名金钥只在 GitHub Secrets 与作者机器上**，不要写进仓库、日志或 PR。
- 商店：IzzyOnDroid 因其 AI 代码政策拒绝收录，作者决定暂不上架商店，只通过 GitHub／Obtainium 发布。`fastlane/` 元数据是当时为 IzzyOnDroid 准备的，保留。

## 未完成或待确认（细节见 QINGSHU.md「已知限制」）

- 尚待实机确认：点选候选、‹ › 翻页、SYM 中文标点、键盘表面滑动。
- 键盘表面滑动选字：调查中，可能需要 Shizuku。
- 用户词库是否真的学到常用词，尚未完全验证。
