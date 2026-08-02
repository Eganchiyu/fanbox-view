# Fanbox Reader（app-v2）

本地 Fanbox 导出内容阅读器。选择 Fanbox 导出根目录后，以卡片网格展示文章（按日期倒序），支持图文混排、GIF 动图、内嵌/全屏视频阅读。

## 功能特性

- **文章列表**：按 `YYYY-MM-DD` 前缀的文件夹识别文章，网格卡片展示封面与标题；「全部 / 收藏」两个标签页。
- **文章阅读**：`LazyColumn` 顺序渲染文本、图片、视频块；支持收藏。
- **HTML 文章**：文件夹内含 `.html`/`.htm` 时自动用 JSoup 解析为块序列，`<img>`/`<video>` 的 `src` 映射为同级文件夹文件。
- **GIF 动图**：阅读页与全屏页均可播放（coil-gif）。
- **全屏图片**：横向翻页 + 双指缩放 + 下滑关闭。
- **视频播放**：内联播放（GSYVideoPlayer）；全屏页（ExoPlayer）支持旋转、双指缩放、2.5 倍速、进度拖动。

## 技术栈

| 项 | 值 |
| --- | --- |
| 语言 | Kotlin（Jetpack Compose + Material3） |
| 包名 | `com.fanbox.reader` |
| minSdk / targetSdk | 26 / 36 |
| AGP | 9.2.1 |
| Gradle | 9.5.1（本地发行版，见 `.gradle-dist/`，无 wrapper） |
| 关键依赖 | coil-compose/gif 2.7.0、jsoup 1.17.2、media3 1.6.1、gsyvideoplayer 11.1.0、documentfile |

## 项目结构

```
app/src/main/java/com/fanbox/reader/
├── AppContext.kt     # Application：全局 context、Coil ImageLoader（GIF 解码）
└── MainActivity.kt   # 全部 UI 与逻辑：Home / Detail / Fullscreen / FullscreenVideo
docs/                 # 开发计划与架构文档
.trae/                # Trae 项目规则（构建方法、约定）
```

## 构建

前置环境：

- JDK 17：`D:\Dev\Env\jdk-17`
- Android SDK：`D:\Dev\Env\android-sdk`（platform `android-36`，build-tools `36.0.0`）
- Gradle 9.5.1：`D:\Projects\fanbox-view\.gradle-dist\gradle-9.5.1\bin\gradle.bat`

PowerShell 构建（产物输出到根目录 `build/`）：

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

## 版本

版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName` 中维护；变更记录见 [CHANGELOG.md](CHANGELOG.md)。
