# 变更记录

本项目的所有重要变更都将记录在此文件中。
格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

## [3.3] - 2026-08-02（当前）

- 构建产物统一输出到根目录 `build/` 文件夹（APK 位于 `build/app/outputs/apk/debug/app-debug.apk`），不再使用模块内 `app/build`。
- 项目初始化：新增 README、CHANGELOG、`.gitignore`，建立 git 仓库。
- 新增 Trae 项目规则（`.trae/rules/project_rules.md`）与架构文档（`docs/ARCHITECTURE.md`）。

## [3.2] - 2026-08-02

- 新增 HTML 文章解析与渲染（jsoup）：文件夹内存在 `.html`/`.htm` 时优先解析，`<img>`/`<video>` 的 `src` 映射为同级文件夹文件，文本保留 HTML 样式。
- 新增 GIF 播放支持（coil-gif），阅读页与全屏页均可播放。
- 优化视频播放：内联播放器（GSYVideoPlayer），新增全屏播放页（ExoPlayer）支持旋转、双指缩放、进度拖动与 2.5 倍速。
