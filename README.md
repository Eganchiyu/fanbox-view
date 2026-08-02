# Fanbox Reader（app-v2）

> 面向本地 Fanbox 导出内容的 Android 阅读器。

将 Fanbox 导出文件夹视作一座馆藏：文件夹即书目，文章即书卷。应用负责陈列、检索与翻阅——图文混排、动图、视频各归其位，全程本地运行，无需网络，数据不离开设备。

---

## 应用场景

本项目面向 Fanbox 内容的本地阅读，其数据来源与项目缘起如下：

- **数据来源**：Fanbox 内容经导出工具生成本地文件，目前主力推荐 [PixivFanboxDownloader](https://github.com/xuejianxianzun/PixivFanboxDownloader)（同样接受其他以「文件夹 + 媒体文件」组织的导出方案）。
- **项目缘起**：该类工具输出为「日期-标题」命名的文件夹，内含 HTML、图片、视频与 GIF，便于归档与备份，却缺乏顺手的阅读体验。本项目即为解决这一痛点而生：将「文件夹 + 媒体文件 + HTML」还原为连贯的文章浏览。
- **当前范围**：专注阅读本身——图片、视频、GIF，以及 HTML 与其内嵌媒体（图片 / 视频 / GIF）的完整呈现。

### 展望

阅读器并不止于 Fanbox。现有「文件夹 → 内容块」的渲染模型天然适用于任意按目录组织的文本与媒体内容，后续规划支持自建内容与自由编排，使本项目逐步扩展为通用阅读器——课堂笔记、小说连载等场景均在其列。

---

## 快速开始

1. 启动应用，选择「选择目录」。
2. 指定 Fanbox 导出根文件夹（目录授权持久化，二次启动免选）。
3. 书架按日期倒序陈列，点击卡片即进入阅读。

---

## 功能概览

| 模块 | 能力 | 说明 |
| --- | --- | --- |
| 书架 | 自动归档 · 收藏夹 · 状态记忆 | 按 `YYYY-MM-DD-标题` 识别文章；「全部 / 收藏」双视图；滚动位置自动恢复 |
| 阅读 | 翻页 · 块序渲染 · HTML 解析 | 文章间横向翻篇；文本 / 图片 / 视频按原始顺序呈现；含 `.html` 时自动解析，资源精准映射同级文件 |
| 看图 | 全屏 · 缩放 · 手势退出 | 横向翻页浏览；双指缩放 1x–5x；单指下滑关闭，与主流看图习惯一致 |
| 看片 | 内联 · 全屏 · 倍速 | 内联播放不打断阅读；全屏横屏模式支持双指缩放、双击快退/快进 10 秒、长按 2.5 倍速、进度拖动，进出全屏保留播放位置 |
| 动图 | GIF 全链路播放 | 封面、阅读页、全屏页均正常播放，非静态帧 |
| 数据 | 本地优先 · 零联网 | 无网络请求；收藏与目录授权仅存于本地 `SharedPreferences` |

## 实现要点

- **块模型驱动渲染**：内容统一抽象为 `Block`（Text / Image / Video / Unsupported），阅读页按序渲染，结构与创作者排版一致。
- **HTML 智能解析**：基于 JSoup 的递归解析器，将 `<img>`、`<video>`、视频链接与富文本段落拆分为块序列，`src` 与同级文件建立映射，兼容大小写与 URL 编码。
- **GIF 解码配置**：通过 Coil `ImageLoader` 按系统版本选择 `ImageDecoderDecoder` / `GifDecoder`，全链路动图支持。
- **视频双引擎**：内联使用 GSYVideoPlayer；全屏页基于 ExoPlayer + `TextureView`，叠加变换层实现画面缩放。
- **目录持久授权**：SAF 目录授权与收藏状态本地持久化，重启免重新选择。

## 开发进度

| 版本 | 阶段 | 里程碑 |
| --- | --- | --- |
| 3.3 | 当前 | 构建产物统一输出至根目录 `build/`；项目初始化（文档、规则、版本控制） |
| 3.2 | 已完成 | HTML 文章解析渲染、GIF 动图播放、全屏视频播放器（旋转 / 缩放 / 倍速） |
| 基础版 | 已完成 | 书架、详情阅读、全屏看图、内联视频等基础能力 |

版本里程碑详情见 [CHANGELOG.md](CHANGELOG.md)。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言与 UI | Kotlin · Jetpack Compose · Material3 |
| 包名 | `com.fanbox.reader`（minSdk 26 / targetSdk 36） |
| 构建 | AGP 9.2.1 · Gradle 9.5.1（本地发行版） |
| 图片 | Coil 2.7.0（compose + gif） |
| HTML 解析 | jsoup 1.17.2 |
| 视频 | media3 (ExoPlayer) 1.6.1 · GSYVideoPlayer 11.1.0 |
| 文件访问 | androidx.documentfile（SAF） |

## 项目结构

```
app/src/main/java/com/fanbox/reader/
├── AppContext.kt     # Application：全局 context、Coil ImageLoader（GIF 解码）
└── MainActivity.kt   # 全部 UI 与逻辑：Home / Detail / Fullscreen / FullscreenVideo
docs/                 # 开发计划与架构文档
.trae/                # 项目规则（构建方法、约定）
```

## 构建

环境要求：

- JDK 17：`D:\Dev\Env\jdk-17`
- Android SDK：`D:\Dev\Env\android-sdk`（platform `android-36`，build-tools `36.0.0`）
- Gradle 9.5.1：`D:\Projects\fanbox-view\.gradle-dist\gradle-9.5.1\bin\gradle.bat`

构建（产物输出至根目录 `build/`）：

```powershell
$env:JAVA_HOME='D:\Dev\Env\jdk-17'
$env:ANDROID_HOME='D:\Dev\Env\android-sdk'
$env:ANDROID_SDK_ROOT='D:\Dev\Env\android-sdk'
& 'D:\Projects\fanbox-view\.gradle-dist\gradle-9.5.1\bin\gradle.bat' assembleDebug --console=plain
```

验证 APK 签名：

```powershell
$env:JAVA_HOME='D:\Dev\Env\jdk-17'
& 'D:\Dev\Env\android-sdk\build-tools\36.0.0\apksigner.bat' verify --verbose 'd:\Projects\fanbox-view\build\app\outputs\apk\debug\app-debug.apk'
```

## 版本维护

版本号在 `app/build.gradle.kts` 中维护（当前 `versionName 3.3` / `versionCode 4`），每次发版同步更新 CHANGELOG，保证版本履历完整可溯。
