package com.picacompose.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
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
    val colors = MaterialTheme.colorScheme

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
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "登录后同步你的收藏、评论和追更进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                LoginCard(controller = controller, onSubmit = onSubmit, onForgotPassword = onForgotPassword)
                Spacer(Modifier.height(6.dp))
                SignUpRow(onRegister)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "同一个 PicACG 账号即可登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BrandRow() {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "PicaCompose",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
            )
            Text(
                text = "PicACG 第三方客户端",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
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
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        tonalElevation = 1.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(
                text = "账号登录",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            EmailField(controller)
            Spacer(Modifier.height(12.dp))
            PasswordField(controller, onSubmit)
            AnimatedVisibility(visible = controller.error != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    ErrorBanner(controller.error.orEmpty())
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSubmit,
                enabled = !controller.loading,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (controller.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("登录中…", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text("登录", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    PicaGlyph(PicaIcon.ArrowRight, Modifier.size(18.dp), colors.onPrimary)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onForgotPassword) {
                    Text("忘记密码？", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmailField(controller: LoginController) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = controller.email,
        onValueChange = controller::onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.loading,
        label = { Text("邮箱 / 用户名") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { PicaGlyph(PicaIcon.Person, Modifier.size(20.dp), colors.onSurfaceVariant) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        colors = picaFieldColors(),
    )
}

@Composable
private fun PasswordField(controller: LoginController, onSubmit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = controller.password,
        onValueChange = controller::onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.loading,
        label = { Text("密码") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { PicaGlyph(PicaIcon.Lock, Modifier.size(20.dp), colors.onSurfaceVariant) },
        trailingIcon = {
            IconButton(onClick = controller::togglePasswordVisible) {
                PicaGlyph(
                    icon = if (controller.passwordVisible) PicaIcon.EyeOff else PicaIcon.Eye,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
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
        colors = picaFieldColors(),
    )
}

@Composable
private fun picaFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun ErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SignUpRow(onRegister: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "还没有账号？",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        TextButton(onClick = onRegister) {
            Text("注册一个", style = MaterialTheme.typography.labelLarge)
        }
    }
}
