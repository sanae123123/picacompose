package com.picacompose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.picacompose.ui.components.PicaAlertDialog
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.NetworkFetcher
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.picacompose.data.LinePreferences
import com.picacompose.picaapi.ImageQuality
import com.picacompose.picaapi.ImageQualitySetting
import com.picacompose.data.LineRouter
import com.picacompose.data.RouteStore
import com.picacompose.data.applyPicaRoute
import com.picacompose.data.pica
import com.picacompose.data.ImageHosts
import com.picacompose.data.ImageRetryInterceptor
import com.picacompose.data.picaNetworkFailureHandler
import com.picacompose.picaapi.imageEngine
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaToast
import com.picacompose.ui.components.PicaToastHost
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.home.HomeShell
import com.picacompose.ui.login.LoginController
import com.picacompose.ui.login.LoginScreen
import com.picacompose.ui.routing.RoutingScreen
import com.picacompose.ui.theme.PicaTheme
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig

/** 启动时要停在哪儿：先试着恢复上次的登录，恢复了就直接进首页。 */
private enum class Screen { Restoring, Login, Home }

/**
 * 应用根组件，Android / iOS / Desktop 共用这一份 UI。
 *
 * 冷启动顺序（设置里的两个开关决定走哪条）：
 * - 自动测速 + 快速进入（默认）：直接按上次的线路建好客户端进主界面，测速丢到后台做完；
 *   期间只要有请求连不上，立刻换成后台测出来能通的那条（见 [LineRouter]）。
 * - 自动测速开、快速进入关：停在线路选择页把速测完再进。
 * - 自动测速关：按上次的线路直接进，不测速、也不会自动换线。
 * - 没记录（首次启动、或上次选的下发线路没留 IP 池）：只能停在线路选择页选一条。
 *
 * 之后才是恢复登录态 → 登录页或首页。
 */
@Composable
fun App() {
    PicaTheme {
        InstallImageLoader()
        val store = remember { RouteStore() }
        // 开关在设置页里改，下一次冷启动才生效，所以这里只在进 App 时读一次。
        val preferences = remember { LinePreferences() }
        // 画质要赶在第一条接口请求之前垫上：路由那次重建也会设，但登录恢复可能比它早。
        LaunchedEffect(Unit) {
            ImageQualitySetting.current = ImageQuality.fromIndex(preferences.imageQuality)
        }
        val autoProbe = remember { preferences.autoProbe }
        val fastEntry = remember { preferences.fastEntry }
        // 下发线路没存 IP 池时会退化成系统解析，那种情况不如老老实实测一次。
        val cachedLine = remember {
            store.savedLine?.takeIf { !it.pinnedDns || store.savedIps.isNotEmpty() }
        }
        val scope = rememberCoroutineScope()
        var routed by remember { mutableStateOf(false) }
        // 有缓存线路时要先把它建成客户端，再让主界面发请求。
        var routeReady by remember { mutableStateOf(cachedLine == null) }

        LaunchedEffect(cachedLine) {
            // 上次跑过 /init 就把图床地址先摆上，别等这次 /init 回来才把首页封面拼对。
            ImageHosts.update(store.savedImageServer)
            picaNetworkFailureHandler = LineRouter::onNetworkFailure
            LineRouter.onNotice = PicaToast::show
            LinePreferences.onDohDisabled = PicaToast::show
            // 没存过线路就只剩选择页可选，连「不测速」也用不了：得先有一条线路才能建客户端。
            if (cachedLine == null) return@LaunchedEffect
            when {
                // 关掉自动测速：停在线路页让人手动点一条（进去之后也不测速、不自己换线）。
                !autoProbe -> routeReady = true

                // 要测速但不要后台测：交给线路选择页，测完自动进（那边会自己建客户端）。
                !fastEntry -> routeReady = true

                // 默认：先按上次的线路进主界面，测速丢后台。
                else -> {
                    runCatching { applyPicaRoute(cachedLine, store.savedIps) }
                    LineRouter.start(scope, cachedLine, store.savedIps)
                    routeReady = true
                    routed = true
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (!routeReady) {
                RestoreScreen()
            } else {
                Crossfade(targetState = routed) { ready ->
                    if (ready) {
                        MainScreens()
                    } else {
                        RoutingScreen(manual = !autoProbe, onReady = { routed = true })
                    }
                }
            }
            PicaToastHost(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 抬高一点，别压到底部导航栏上。
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 100.dp),
            )
        }
    }
}

@Composable
private fun MainScreens() {
    // 传 lambda 而不是客户端本身：换线路会把全局 `pica` 整个换掉。
    val controller = remember { LoginController({ pica }) }
    var pendingFeature by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { controller.restoreSession() }

    val screen = when {
        controller.restoring -> Screen.Restoring
        controller.profile == null -> Screen.Login
        else -> Screen.Home
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.background,
    ) {
        Crossfade(targetState = screen) { current ->
            when (current) {
                Screen.Restoring -> RestoreScreen()
                Screen.Login -> LoginScreen(
                    controller = controller,
                    onForgotPassword = { pendingFeature = "找回密码" },
                    onRegister = { pendingFeature = "注册" },
                )

                Screen.Home -> controller.profile?.let { profile ->
                    HomeShell(
                        profile = profile,
                        onSignOut = controller::signOut,
                        onRefreshProfile = { controller.refreshProfile() },
                    )
                }
            }
        }
    }

    pendingFeature?.let { feature ->
        val pop = rememberDialogPop(onDismiss = { pendingFeature = null })
        PicaAlertDialog(
            onDismissRequest = pop::dismiss,
            modifier = pop.modifier,
            title = { Text("$feature 还没做") },
            text = { Text("先把登录流程跑通，$feature 页面排在下一步。") },
            confirmButton = {
                PicaTextButton(onClick = pop::dismiss) { Text("好") }
            },
        )
    }
}

/**
 * 装应用自己的图片加载器：失败重试一次；开了 DoH 就让图片也走 DoH。
 *
 * Coil 默认那个 ktor 客户端是它自己建的，没法从外面改 DNS，只能自己塞一个带 DoH 引擎的
 * fetcher 进单例加载器；开关关着就只加重试，不多建网络客户端。
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun InstallImageLoader() {
    val preferences = remember { LinePreferences() }
    val fetcher = remember { imageFetcher() }
    val factory = remember(fetcher) {
        { context: PlatformContext ->
            ImageLoader.Builder(context).components {
                add(ImageRetryInterceptor())
                add(fetcher)
            }.build()
        }
    }
    setSingletonImageLoaderFactory(factory)
}

/**
 * 图片专用的 Ktor 客户端：DNS 用 [imageEngine]（picacomic 的图床域名钉到一组能连的 IP），
 * 不走 DoH —— DoH 开关管的是接口那条链路，图片这边钉子地址更直接。
 *
 * 关键是**一定有超时**。Coil 默认那个客户端是 Ktor 现搭的，超时按引擎默认来；
 * 移动网络下连接被挂住的时候（图床在切节点、或者半路丢包），请求会一直不返回，
 * 界面就永远停在 Loading，连错误都拿不到 —— 看起来就是「一直转圈」。
 * 官方客户端用 OkHttp，自带 10 秒连接超时，所以同样的网络它要么出图要么报错。
 *
 * 图床的时限比接口宽松：一屏图一起加载，掐太紧会误伤本来就慢的图。
 */
@OptIn(ExperimentalCoilApi::class)
private fun imageFetcher(): NetworkFetcher.Factory {
    val engine = imageEngine()
    val timeouts: HttpTimeoutConfig.() -> Unit = {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    val client = if (engine != null) {
        HttpClient(engine) { install(HttpTimeout) { timeouts() } }
    } else {
        HttpClient { install(HttpTimeout) { timeouts() } }
    }
    return KtorNetworkFetcherFactory(httpClient = client)
}

/** 拿旧 token 恢复会话时的一小段过渡，免得先闪一下登录页。 */
@Composable
private fun RestoreScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PicaBackdrop()
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = MiuixTheme.colorScheme.primary),
        )
    }
}
