# Fanbox Reader 架构简明文档

## 1. 定位

本地 Fanbox 导出内容阅读器。用户通过系统目录选择器授权访问导出根目录，应用读取其中按 `YYYY-MM-DD-标题` 命名的文章文件夹，以「网格列表 → 文章详情 → 全屏图片/视频」的层级进行阅读。

## 2. 模块与代码组织

单模块 `:app`，包 `com.fanbox.reader`，全部 UI 与逻辑集中在两个文件：

| 文件 | 职责 |
| --- | --- |
| `AppContext.kt` | `Application` 子类：全局 `context`；`ImageLoaderFactory` 配置 Coil（SDK>=28 用 `ImageDecoderDecoder`，否则 `GifDecoder`）以支持 GIF |
| `MainActivity.kt` | 数据加载、屏幕导航、全部 Composable 界面 |

## 3. 数据模型与加载

- **`Post`**：一篇文章 = 一个文件夹，字段 `folder / title / date / cover / blocks`。
- **`Block`**（sealed interface）：内容单元，四种类型：
  - `Text(content, isHtml)`：文本；`isHtml=true` 时用 `TextView + Html.fromHtml` 渲染（保留样式/链接）。
  - `Image(file)`：`DocumentFile` 指向图片文件，用 `AsyncImage` 显示。
  - `Video(file)`：视频文件。
  - `Unsupported(name)`：未知类型文件占位。
- **加载流程**：`loadPosts()` 在子线程扫描文件夹 → 先返回快速列表（封面未解析）→ 再逐篇 `withBlocks()` 生成完整 `blocks` 后刷新。
- **`withBlocks()`**：若文件夹内存在 `.html`/`.htm`，用 JSoup 解析为块序列；否则按文件名排序生成块。封面取第一张图片（无则取第一个视频）。

## 4. HTML 解析

`parseHtmlBlocks()` 遍历 body 子节点，将内容转为 `Block` 列表：

- `<img>`（含 `data-src`/`data-original`）→ `Block.Image`；`src` 解码后取文件名，映射到同级文件夹文件（`resolveFile`，忽略大小写兜底）。
- `<video>` / `<source>`、`<a href="*.mp4">` 视频链接 → `Block.Video`。
- 文本节点聚合为 `Block.Text`（保留原始 HTML 片段）。
- `<br>`/`<hr>` 保留；混合容器递归拆分为文本 + 媒体块。

## 5. 屏幕导航

- `BackStack`（单例，`mutableStateOf<Screen>`）：手工栈式导航，系统返回键弹出。
- `Screen` 四态：`Home`、`Detail(index)`、`Fullscreen(images, index)`、`FullscreenVideo(uri, position)`。
- 文章详情用 `HorizontalPager` 横向翻页；进入/翻页时 `BackStack.replace` 保持页面状态。

## 6. 主要界面

| 界面 | 说明 |
| --- | --- |
| `HomeScreen` | `Scaffold` + TabRow（全部/收藏）+ `LazyVerticalGrid` 卡片网格；滚动位置存 `HomeMemory` 恢复 |
| `DetailScreen` | `LazyColumn` 按序渲染 `blocks`，顶部标题与页码，右上收藏 |
| `FullscreenScreen` | 黑底 `HorizontalPager` 全屏翻图，每页 `ZoomImage` 双指缩放（1x–5x），单指下滑退出 |
| `FullscreenVideoScreen` | 黑底全屏 ExoPlayer：`TextureView` 上叠加 `transformable` 缩放，点击控制栏、双击快进/快退 10s、长按 2.5 倍速；进入锁定横屏 |

## 7. 视频播放

- 内联：`InlineVideoPlayer` 用 GSYVideoPlayer（隐藏全屏按钮，自绘「全屏」入口），暂停/恢复跟随生命周期。
- 全屏：`FullscreenVideoScreen` 用 ExoPlayer + `TextureView`；进入/退出传递播放位置；`DisposableEffect` 释放 player、恢复屏幕方向。

## 8. 数据持久化

- 目录授权：`SharedPreferences("storage")` 存 `tree_uri`，配合 `takePersistableUriPermission` 重启后免选。
- 收藏：`SharedPreferences("favorites")` 存文章 folder URI 集合。

## 9. 构建

- 环境：JDK 17、Android SDK（platform 36）、Gradle 9.5.1（`.gradle-dist/` 本地发行版）。
- 产物统一输出到根目录 `build/`（`app/build.gradle.kts` 中 `layout.buildDirectory` 重定向）；Debug APK 位于 `build/app/outputs/apk/debug/app-debug.apk`。
- 详见 `.trae/rules/project_rules.md` 中的构建命令。
