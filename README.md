# PicaCompose

[![build](https://github.com/sanae123123/picacompose/actions/workflows/build.yml/badge.svg)](https://github.com/sanae123123/picacompose/actions/workflows/build.yml)

Compose Multiplatform 跨平台项目 —— 一份 Kotlin 代码，同时跑在 **Android / iOS / Desktop** 上。

## 技术栈

| 组件 | 版本 |
| --- | --- |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.6.1 |
| Android SDK | compileSdk 37 / minSdk 24 |

## 目录结构

```
picacompose/
├── shared/                      # 共享模块：UI 与业务逻辑都写在这里
│   ├── src/commonMain/kotlin/com/picacompose/App.kt        # 公共 UI
│   └── src/iosMain/kotlin/com/picacompose/MainViewController.kt
├── androidApp/                  # Android 宿主（MainActivity + Manifest）
├── desktopApp/                  # 桌面宿主（JVM 窗口入口）
├── gradle/libs.versions.toml    # 版本统一管理
└── .github/workflows/build.yml  # CI：推上去自动编译
```

新增界面时，优先改 `shared/src/commonMain`，三个平台会同时生效；只有确实需要平台 API 时才在 `androidMain` / `iosMain` 里写 `expect/actual`。

## 环境要求

- JDK 17 及以上（项目按 JDK 21 验证）
- Android 端：Android Studio（自带 SDK / JDK）
- 桌面端：任意 JDK 17+，无需 Android SDK
- iOS 端：macOS + Xcode（详见下方说明）

## 运行

```bash
# 桌面版（最快的验证方式）
./gradlew :desktopApp:run

# Android 调试包
./gradlew :androidApp:assembleDebug
# 产物：androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 桌面安装包（dmg / msi / deb）
./gradlew :desktopApp:packageDistributionForCurrentOS
```

也可以直接用 Android Studio 打开仓库根目录，等 Gradle 同步完成后选 `androidApp` 运行。

## iOS

`shared` 已经配好 `iosArm64` 与 `iosSimulatorArm64` 两个 target，框架名为 `Shared`（静态），入口是 `shared/src/iosMain/kotlin/com/picacompose/MainViewController.kt`。

Xcode 工程（`iosApp.xcodeproj`）还没建：生成它需要在 macOS 上跑，没法在 Linux/CI 里可靠地产出。要用 iOS 时，在 macOS 上用 Android Studio 的 Kotlin Multiplatform 插件新建一个 iOS App target，把 `MainViewController` 作为 `UIViewControllerRepresentable` 的根视图，并在 Build Phases 里加上编译 `Shared` 框架的 `embedAndSignAppleFrameworkForXcode` 脚本即可。


## 依赖说明

### picacomic-api

- 坐标 `com.hiczp:picacomic-api:0.0.1`（Maven Central，Apache-2.0，作者 [czp3009](https://github.com/czp3009/picacomic-api)）。
- 纯 JVM 库（ktor 1.2.6 + caeruleum + gson + kotson），只挂在 `shared` 的 `androidMain` / `jvmMain`；`commonMain` 与 iOS 用不了，iOS 端要另写基于 Ktor 3 的客户端。
- 原版 jar 在 Android 上 dex 不过：`PredefinedCategory` 枚举的常量名带空格（`英語 ENG`、`Love Live`、`SAO 刀劍神域`），D8 对 minSdk < 30 拒绝这种字段名。`maven/` 里放了一份删掉该枚举的改写版（该枚举在库里没有任何引用），`settings.gradle.kts` 让本地仓库优先解析 `com.hiczp`；要重新生成：

  ```bash
  python3 tools/patch-picacomic-api.py
  ```
- 库内部用 `java.time`，所以 `androidApp` 开启了 core library desugaring。
- Android 端引擎用 OkHttp，桌面端用 CIO，入口在 `shared/src/{androidMain,jvmMain}/kotlin/com/picacompose/data/PicaApi.kt`。

## 说明

- 版本号集中在 `gradle/libs.versions.toml`，升级只改这一处。
- `shared` 里预置了一个计数器页面，只用来做冒烟验证，业务代码直接替换 `CounterScreen()` 即可。
