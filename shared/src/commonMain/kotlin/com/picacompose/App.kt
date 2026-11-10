package com.picacompose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.picacompose.data.LinePreferences
import com.picacompose.data.LineRouter
import com.picacompose.data.RouteStore
import com.picacompose.data.applyPicaRoute
import com.picacompose.data.pica
import com.picacompose.data.picaNetworkFailureHandler
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaToast
import com.picacompose.ui.components.PicaToastHost
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.home.HomeShell
import com.picacompose.ui.login.LoginController
import com.picacompose.ui.login.LoginScreen
import com.picacompose.ui.routing.RoutingScreen
import com.picacompose.ui.theme.PicaTheme

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
        val store = remember { RouteStore() }
        // 开关在设置页里改，下一次冷启动才生效，所以这里只在进 App 时读一次。
        val preferences = remember { LinePreferences() }
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
            picaNetworkFailureHandler = LineRouter::onNetworkFailure
            LineRouter.onNotice = PicaToast::show
            // 没存过线路就只剩选择页可选，连「不测速」也用不了：得先有一条线路才能建客户端。
            if (cachedLine == null) return@LaunchedEffect
            when {
                // 关掉自动测速：按上次的线路建好客户端直接进；不测速，但断线时还能重建这条线。
                !autoProbe -> {
                    runCatching { applyPicaRoute(cachedLine, store.savedIps) }
                    LineRouter.start(scope, cachedLine, store.savedIps, probe = false)
                    routeReady = true
                    routed = true
                }

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
                    if (ready) MainScreens() else RoutingScreen(onReady = { routed = true })
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
        color = MaterialTheme.colorScheme.background,
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
        AlertDialog(
            onDismissRequest = pop::dismiss,
            modifier = pop.modifier,
            title = { Text("$feature 还没做") },
            text = { Text("先把登录流程跑通，$feature 页面排在下一步。") },
            confirmButton = {
                TextButton(onClick = pop::dismiss) { Text("好") }
            },
        )
    }
}

/** 拿旧 token 恢复会话时的一小段过渡，免得先闪一下登录页。 */
@Composable
private fun RestoreScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PicaBackdrop()
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
