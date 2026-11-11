package com.picacompose.ui.login

import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaButton
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.PicaCircularProgress
import com.picacompose.ui.components.PicaCheckbox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import com.picacompose.ui.components.PicaTextButton
import com.picacompose.ui.components.PicaTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.launch

/**
 * 登录页：Material You 观感 —— 渐变底、超大圆角卡片、胶囊按钮。
 */
@Composable
fun LoginScreen(
    controller: LoginController,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val onSubmit: () -> Unit = { scope.launch { controller.signIn() } }
    val colors = MiuixTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                BrandRow()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "欢迎回来",
                    style = MiuixTheme.textStyles.title1,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "登录后同步你的收藏、评论和追更进度。",
                    style = MiuixTheme.textStyles.body1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(20.dp))
                LoginCard(controller = controller, onSubmit = onSubmit, onForgotPassword = onForgotPassword)
                Spacer(Modifier.height(6.dp))
                SignUpRow(onRegister)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "同一个 PicACG 账号即可登录",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BrandRow() {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(colors.primary, colors.tertiaryContainer))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                style = MiuixTheme.textStyles.title3,
                color = colors.onPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "PicaCompose",
                style = MiuixTheme.textStyles.title4,
                color = colors.onBackground,
            )
            Text(
                text = "PicACG 第三方客户端",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
        }
    }
}

@Composable
private fun LoginCard(
    controller: LoginController,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.cardRadius),
        color = colors.tertiaryContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(
                text = "账号登录",
                style = MiuixTheme.textStyles.title4,
                color = colors.onSurfaceSecondary,
            )
            Spacer(Modifier.height(14.dp))
            EmailField(controller)
            Spacer(Modifier.height(12.dp))
            PasswordField(controller, onSubmit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RememberRow(controller, Modifier.weight(1f))
                PicaTextButton(onClick = onForgotPassword) {
                    Text("忘记密码？", style = MiuixTheme.textStyles.body1)
                }
            }
            AnimatedVisibility(visible = controller.error != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    ErrorBanner(controller.error.orEmpty())
                }
            }
            Spacer(Modifier.height(20.dp))
            PicaButton(
                onClick = onSubmit,
                enabled = !controller.loading,
                cornerRadius = 999.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (controller.loading) {
                    PicaCircularProgress(
color = colors.onPrimary,
modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("登录中…", style = MiuixTheme.textStyles.button)
                } else {
                    Text("登录", style = MiuixTheme.textStyles.button)
                    Spacer(Modifier.width(8.dp))
                    PicaGlyph(PicaIcon.ArrowRight, Modifier.size(18.dp), colors.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun EmailField(controller: LoginController) {
    val colors = MiuixTheme.colorScheme
    PicaTextField(
        value = controller.email,
        onValueChange = controller::onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.loading,
        placeholder = "邮箱 / 用户名",
        singleLine = true,
        cornerRadius = 16.dp,
        leadingIcon = { PicaGlyph(PicaIcon.Person, Modifier.size(20.dp), colors.onSurfaceSecondary) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun PasswordField(controller: LoginController, onSubmit: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    PicaTextField(
        value = controller.password,
        onValueChange = controller::onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.loading,
        placeholder = "密码",
        singleLine = true,
        cornerRadius = 16.dp,
        leadingIcon = { PicaGlyph(PicaIcon.Lock, Modifier.size(20.dp), colors.onSurfaceSecondary) },
        trailingIcon = {
            PicaIconButton(onClick = controller::togglePasswordVisible) {
                PicaGlyph(
                    icon = if (controller.passwordVisible) PicaIcon.EyeOff else PicaIcon.Eye,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceSecondary,
                )
            }
        },
        visualTransformation = if (controller.passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
    )
}

@Composable
private fun RememberRow(controller: LoginController, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !controller.loading) {
                controller.onRememberChange(!controller.rememberAccount)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaCheckbox(
            checked = controller.rememberAccount,
            onCheckedChange = { controller.onRememberChange(it) },
            enabled = !controller.loading,
        )
        Text(
            text = "记住账号",
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurfaceSecondary,
        )
    }
}


@Composable
private fun ErrorBanner(message: String) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(12.dp),
        color = colors.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onErrorContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SignUpRow(onRegister: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "还没有账号？",
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurfaceSecondary,
        )
        PicaTextButton(onClick = onRegister) {
            Text("注册一个", style = MiuixTheme.textStyles.button)
        }
    }
}
