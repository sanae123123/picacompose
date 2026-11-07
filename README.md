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
| Ktor | 3.6.1（client，Android=OkHttp / JVM=CIO / iOS=Darwin） |
| kotlinx.serialization | 1.12.0 |

## 目录结构

```
picacompose/
├── picaapi/                     # PicACG API 客户端（纯 KMP，Android/JVM/iOS 通用）
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


## picaapi：PicACG API 客户端

`picaapi/` 是一个纯 Kotlin Multiplatform 的 PicACG 客户端，参考这两个项目重写：

- [czp3009/picacomic-api](https://github.com/czp3009/picacomic-api)（Ktor 1.x，JVM-only）
- [qiunanrima/PicACG-Api-Java](https://github.com/qiunanrima/PicACG-Api-Java)（OkHttp + Gson，JVM-only）
- [shizq123/BIKA](https://github.com/shizq123/BIKA) 的 `core/network`（Ktor 3 + 协程，补全了注册、合集、骑士榜、高级搜索、通知、我的评论等接口）

和它们最大的区别：**全部代码在 `commonMain`**，Android / JVM / iOS 共用一份，
签名算法（HMAC-SHA256）和 SHA-256 也是纯 Kotlin 实现，不依赖任何平台的加密库。

```kotlin
val pica = PicaClient()

pica.auth.signIn("email", "password")            // token 自动保存
val page = pica.comics.list(page = 1, category = "全彩", sort = "dd")
val detail = pica.comics.detail(page.docs.first().id)
val eps = pica.comics.episodes(detail.id)
val pages = pica.comics.pages(detail.id, order = eps.docs.first().order)
val url = pica.imageUrl(pages.pages.docs.first().media)
```

已实现的接口：登录 / 注册、初始化 / 分类 / 轮播 / 热搜 / 合集、漫画列表与检索 / 高级搜索、
随机推荐、排行榜 / 骑士榜、详情、章节、按章节取图、相关推荐、点赞与收藏、我的收藏、
评论列表 / 楼中楼 / 发评论 / 回复 / 点赞 / 我的评论、用户资料 / 改签名 / 改密码 / 签到 / 通知。
缺的是：头像与 ID 改写、游戏、公告与 pica-apps、评论隐藏/举报/置顶、utils 下的管理接口，
以及聊天室（socket.io 是另一套协议）。

几个实现细节：

- 签名原文是 `(path + time + nonce + method + apiKey).lowercase()`，`path` 含 query 且不带前导斜杠；
  `PicaSignature.sign()` 里就是这个公式，`CryptoTest` 里有固定测试向量。
- `time` 用本地时钟；客户端会从响应头 `Server-Time` 自动算偏移量并持续校正，所以手机时间不准也能用。
- POST 的 `Content-Type` 必须是 `application/json; charset=UTF-8`（大写）。写成小写 `utf-8`
  或没有 charset，服务端会返回 400/1023 "too many requests"，登录会被误判成限流——这是踩过的坑。
- 响应统一是 `{ code, message, data }` 信封，`PicaClient` 负责解信封，失败抛 `PicaException`。
- 所有 JSON 模型都用 `ignoreUnknownKeys` + 默认值，服务端加字段不会炸。

## 说明

- 版本号集中在 `gradle/libs.versions.toml`，升级只改这一处。
- `shared` 里预置了一个计数器页面，只用来做冒烟验证，业务代码直接替换 `CounterScreen()` 即可。
