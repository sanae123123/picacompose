package com.picacompose

import androidx.compose.animation.Crossfade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.picacompose.data.pica
import com.picacompose.ui.home.WelcomeScreen
import com.picacompose.ui.login.LoginController
import com.picacompose.ui.login.LoginScreen
import com.picacompose.ui.theme.PicaTheme

/**
 * 应用根组件，Android / iOS / Desktop 共用这一份 UI。
 *
 * 目前只有两屏：未登录 → 登录页；已登录 → 落脚页（首页下一步补）。
 */
@Composable
fun App() {
    PicaTheme {
        val controller = remember { LoginController(pica) }
        var pendingFeature by remember { mutableStateOf<String?>(null) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Crossfade(targetState = controller.profile) { profile ->
                if (profile == null) {
                    LoginScreen(
                        controller = controller,
                        onForgotPassword = { pendingFeature = "找回密码" },
                        onRegister = { pendingFeature = "注册" },
                    )
                } else {
                    WelcomeScreen(profile = profile, onSignOut = controller::signOut)
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
