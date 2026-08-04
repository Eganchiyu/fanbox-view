plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 构建产物统一输出到根目录 build/ 文件夹（APK 位于 build/app/outputs/apk/...）
layout.buildDirectory.set(layout.projectDirectory.dir("../build/app"))

android { namespace = "com.fanbox.reader"; compileSdk = 36
    defaultConfig { applicationId = "com.fanbox.reader"; minSdk = 26; targetSdk = 36; versionCode = 4; versionName = "3.3" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")
}
