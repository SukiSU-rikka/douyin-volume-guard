# 抖音音量守卫（DouyinVolumeGuard）v2.1 — muse 构建说明

## 这是什么
LSPosed 模块，**libxposed 现代 API 102**（目标框架：LSPosed 2.2.1 (7912)，API 102）。
作用域：抖音 `com.ss.android.ugc.aweme`、极速版 `com.ss.android.ugc.aweme.lite`。
在抖音进程内拦截所有"调大系统音量 / 取消静音"的调用；用户按音量键后 1 秒内放行，调低始终放行。

## 环境基线
- AGP 8.13.0 + Gradle 8.13
- compileSdk 37（需安装 Android 17 platform 包），targetSdk 35，minSdk 28
- JDK 17，纯 Java，无 Kotlin、无 AndroidX

## 依赖（都在 Maven Central）
- `compileOnly("io.github.libxposed:api:102.0.0")`：Hook 端，必须 compileOnly，不能打进 APK
- `implementation("io.github.libxposed:service:102.0.0")`：界面端，和 LSPosed 通信（激活状态、远程配置），要打包

## v2.1 新增
- 桌面图标：自适应图标（`mipmap-anydpi-v26/ic_launcher.xml`，含 monochrome 主题图标层）
- 启动界面 `MainActivity`：激活状态卡、拦截开关、按键放行时长、日志开关、一键重启抖音（su）
- 设置走 libxposed 远程配置（组名 `settings`），界面写、Hook 端 `getRemotePreferences` 只读，改完即时生效
- 纯 framework 控件（Switch / SeekBar），无 AndroidX、无 Material 库

## 模块元数据（API 102 新格式，不再用 xposed_init / meta-data）
放在 `app/src/main/resources/META-INF/xposed/`，Gradle 会自动打进 APK 的 `META-INF/xposed/`：
- `java_init.list`：入口类 `io.github.dyvolguard.MainHook`
- `module.prop`：`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`exceptionMode=protective`
- `scope.list`：两个抖音包名
- 模块名 / 描述来自 Manifest 的 `android:label` / `android:description`

## 步骤
1. 生成 wrapper：`gradle wrapper --gradle-version 8.13`
2. `./gradlew :app:assembleRelease`
3. 产物：`app/build/outputs/apk/release/app-release.apk`（debug 签名）

## 需要 muse 核对的地方（libxposed service 102 的 API 细节）
1. `XposedServiceHelper.registerListener(OnServiceListener)`、`onServiceBind(XposedService)` / `onServiceDied(XposedService)`、
   `XposedService.getRemotePreferences(String)`、`getFrameworkName()`、`getFrameworkVersion()`：
   若方法名或签名与 service 102.0.0 实际不符，按实际 API 改 `App.java` / `MainActivity.java`，逻辑不变
2. **XposedProvider**：检查合并后的 Manifest（`app/build/intermediates/merged_manifest/...`）里有没有
   `io.github.libxposed.service.XposedProvider`。AAR 若没自带，就按 libxposed/service README 手动在 Manifest 里声明，
   否则 LSPosed 递不过来服务，界面会一直显示"未激活"
3. service AAR 若依赖 kotlin-stdlib，正常会被传递引入，无需加 Kotlin 插件

## 检查点
- `unzip -l app-release.apk | grep META-INF/xposed` 必须看到 java_init.list、module.prop、scope.list 三个文件
  （注意：有些 AGP 打包配置会排除 META-INF，若缺失，检查 packaging.resources.excludes，别把 META-INF/xposed/** 排掉）
- APK 里不能包含 io/github/libxposed/api 的类（确认是 compileOnly）；io/github/libxposed/service 的类应该在
- 装上后桌面能看到粉色盾牌喇叭图标
- 若编译报 `Chain.getArg` 返回类型相关错误：代码里统一先接成 `Object` 再强转，按实际签名微调即可，逻辑不变
