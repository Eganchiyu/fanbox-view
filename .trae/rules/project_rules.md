# 项目规则（Fanbox Reader / app-v2）

## 项目概述

- Android 本地阅读器：浏览 Fanbox 导出文件夹（Kotlin + Jetpack Compose + Material3）。
- 包名 `com.fanbox.reader`，单模块 `:app`；源码在 `app/src/main/java/com/fanbox/reader/`。
- 核心逻辑集中在 `MainActivity.kt`（Home / Detail / Fullscreen / FullscreenVideo），`AppContext.kt` 提供全局 context 与 Coil ImageLoader（GIF 解码）。

## 构建环境

- JDK：`D:\Dev\Env\jdk-17`
- Android SDK：`D:\Dev\Env\android-sdk`（platform `android-36`，build-tools `36.0.0`）
- Gradle：`D:\Projects\fanbox-view\.gradle-dist\gradle-9.5.1\bin\gradle.bat`（本地发行版，项目无 wrapper）

## 构建命令（PowerShell）

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

## 构建输出约定

- 所有构建产物统一输出到根目录 `/build` 文件夹（`app/build.gradle.kts` 中通过 `layout.buildDirectory` 配置）。
- Debug APK 路径固定为 `build/app/outputs/apk/debug/app-debug.apk`；禁止引用模块内 `app/build` 路径。

## 代码约定

- 版本号在 `app/build.gradle.kts` 维护：`versionName` 形如 `"3.x"`，`versionCode` 同步递增。
- 注释与文档使用中文；新增 UI 尽量复用现有 `Block`（Text / Image / Video / Unsupported）渲染体系。
- 依赖集中在 `app/build.gradle.kts` 声明，避免引入不必要的库。
- 构建产物、IDE 配置、本地 Gradle 发行版不入库（见 `.gitignore`）。
