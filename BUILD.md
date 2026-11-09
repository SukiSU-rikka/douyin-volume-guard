# 抖音音量守卫（DouyinVolumeGuard）— muse 构建说明

## 这是什么
LSPosed 模块（经典 Xposed API 82），作用域 抖音 `com.ss.android.ugc.aweme`（附带极速版 `.lite`）。
在抖音进程内拦截所有"调大系统音量 / 取消静音"的调用；用户按音量键后 1 秒内放行，调低始终放行。

## 环境基线
- AGP 8.13.0 + Gradle 8.13
- compileSdk 37（需安装 Android 17 platform 包），targetSdk 35，minSdk 28
- JDK 17
- 纯 Java，无 Kotlin 插件、无 AndroidX

## 依赖
- `compileOnly("de.robv.android.xposed:api:82")`，仓库 `https://api.xposed.info/`（已写在 settings.gradle.kts）
- 必须是 compileOnly，不能打进 APK

## 步骤
1. 仓库内没有 gradle wrapper，先生成：`gradle wrapper --gradle-version 8.13`
2. `./gradlew :app:assembleRelease`
3. 产物：`app/build/outputs/apk/release/app-release.apk`（用 debug 签名，个人使用足够）

## 检查点
- APK 里必须有 `assets/xposed_init`，内容为 `io.github.dyvolguard.MainHook`
- Manifest 里有 `xposedmodule` / `xposedminversion=82` / `xposedscope` 三个 meta-data
- 如果 api.xposed.info 拉不到，可改用 jitpack 或本地 `libs/api-82.jar`（仍然 compileOnly）

## 文件结构
```
settings.gradle.kts
build.gradle.kts
gradle.properties
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/xposed_init
app/src/main/res/values/strings.xml
app/src/main/res/values/arrays.xml      # xposed_scope
app/src/main/java/io/github/dyvolguard/MainHook.java
```
