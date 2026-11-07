package com.picacompose

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.home.WelcomeScreen
import com.picacompose.ui.login.LoginController
import com.picacompose.ui.login.LoginScreen
import com.picacompose.ui.theme.PicaTheme

/** 启动时要停在哪儿：先试着恢复上次的登录，恢复了就直接进首页。 */
private enum class Screen { Restoring, Login, Home }

/**
 * 应用根组件，Android / iOS / Desktop 共用这一份 UI。
 *
 * 目前只有两屏：未登录 → 登录页；已登录 → 落脚页（首页下一步补）。
 * 登录时勾了「记住账号」，下次启动会先拿存着的 token 试一次，成功就直接进首页。
 */
@Composable
fun App() {
    PicaTheme {
        val controller = remember { LoginController(pica) }
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
                        WelcomeScreen(profile = profile, onSignOut = controller::signOut)
                    }
                }
            }
        }

        pendingFeature?.let { feature ->
            AlertDialog(
                onDismissRequest = { pendingFeature = null },
                title = { Text("$feature 还没做") },
                text = { Text("先把登录流程跑通，$feature 页面排在下一步。") },
                confirmButton = {
                    TextButton(onClick = { pendingFeature = null }) { Text("好") }
                },
            )
        }
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
