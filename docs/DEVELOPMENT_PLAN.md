# Fanbox Reader HTML / GIF / 视频优化开发计划

## 1. 目标

- 当文章文件夹内存在 HTML 文件时，优先解析并渲染该 HTML；HTML 中引用的图片、视频仍位于同级文件夹，通过文件名映射为 `DocumentFile` URI 后交给现有组件展示。
- 保持现有文章浏览逻辑（`LazyColumn`）与图片查看逻辑（全屏横向翻页 + 双指缩放）。
- GIF 在阅读页与全屏查看页都能正常播放。
- HTML 中内嵌的视频能够被识别并播放。
- 视频播放器支持全屏、屏幕旋转、双指缩放。

## 2. 现状

- 所有 UI 集中在 `MainActivity.kt`。
- 文章详情使用 `LazyColumn` 展示 `Post.entries`（文件列表）。
- 图片使用 `coil.compose.AsyncImage`；全屏使用 `HorizontalPager` + 自定义 `ZoomImage`。
- 视频使用 `AndroidView` 包装 `PlayerView` + `ExoPlayer`，仅支持内联基础播放。
- 依赖：`coil-compose:2.7.0`、`media3-exoplayer:1.6.1`、`media3-ui:1.6.1`。

## 3. 分步方案

### Step 1：HTML 检测与解析

- 在 `Post.withEntries()` 中检测文件夹内是否存在 `.html`/`.htm` 文件。
- 若存在，则读取 HTML 内容，使用 **JSoup** 解析为结构化块列表 `List<HtmlBlock>`：
  - `HtmlBlock.Text(String html)`：保留原始 HTML 段落文本。
  - `HtmlBlock.Image(Uri uri)`：从 `<img src="...">` 提取文件名，映射为同级文件夹内文件 URI。
  - `HtmlBlock.Video(Uri uri)`：从 `<video>`/`<source>` 提取文件名，映射为 URI。
- 同时生成：
  - `cover`：HTML 中第一张图片。
  - 图片索引表：用于全屏查看时正确定位当前图片在所有图片中的位置。
- 若不存在 HTML，保持现有按文件排序渲染逻辑。

### Step 2：GIF 支持

- Gradle 添加 `io.coil-kt:coil-gif:2.7.0`。
- `AsyncImage` 使用的 `ImageLoader` 默认支持 GIF；阅读页与 `FullscreenScreen` 无需额外改动即可播放 GIF。
- 全屏缩放时保持 GIF 动画。

### Step 3：HTML 渲染集成

- `DetailScreen` 区分两种模式：
  - **传统模式**：`entries` 列表。
  - **HTML 模式**：`htmlBlocks` 列表，按块顺序渲染。
- 每个块复用现有组件：
  - `HtmlBlock.Image` -> `AsyncImage`（可点击打开全屏）。
  - `HtmlBlock.Video` -> 优化后的视频播放器。
  - `HtmlBlock.Text` -> 使用 `AndroidView` 包装 `TextView` + `Html.fromHtml`，保持图文混排样式（字体、链接、加粗等）。
- 图片点击时收集 HTML 中所有图片 URI，传入 `FullscreenScreen`，并计算当前索引。

### Step 4：视频播放器优化

- 重构 `AndroidViewPlayer` 为 `InlineVideoPlayer(uri, onFullscreen)`：
  - 使用 `PlayerView` + `ExoPlayer`。
  - 启用默认控制器，包含全屏按钮。
  - 全屏回调把当前 URI 与播放位置传入新屏幕。
- 新增 `FullscreenVideoScreen(uri: Uri)`：
  - 黑色背景，沉浸式全屏。
  - 使用 `SurfaceView` + `ExoPlayer` 渲染视频。
  - 外层包裹 `transformable` + `graphicsLayer` 实现双指缩放与平移。
  - 顶部返回按钮，中央播放/暂停按钮，底部进度条与当前时间/总时长。
  - 进入时锁定方向为 `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出时恢复。
- 播放位置同步：进入/退出全屏时通过 `rememberSaveable` 或回调传递当前 `currentPosition`，避免从头播放。

### Step 5：联调与边界处理

- 处理 HTML 中相对路径、`./`、URL 编码文件名。
- 处理 HTML 中引用的文件不存在时显示占位图/错误提示。
- 处理无 HTML 时回退到原有文件列表。
- 确保 ExoPlayer 在离开页面时释放，避免内存泄漏。

## 4. 关键改动文件

- `app/build.gradle.kts`：新增 `jsoup`、`coil-gif`。
- `app/src/main/java/com/fanbox/reader/MainActivity.kt`：HTML 解析、渲染分发、视频播放器、全屏视频屏幕。
- `app/src/main/AndroidManifest.xml`：可选，如需要固定全屏方向时再调整（计划通过代码动态设置）。

## 5. 风险与回退

- HTML 结构未知：JSoup 解析规则以常见 Fanbox 导出结构为假设；如遇到特殊标签可再扩展 `HtmlBlock`。
- 视频手势冲突：全屏缩放使用透明手势层，点击穿透给播放/暂停；如出现冲突再调整事件分发。
- 若某步引入严重问题，可回退到上一步提交。
