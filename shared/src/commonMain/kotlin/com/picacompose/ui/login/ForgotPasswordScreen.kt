package com.picacompose.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.BackHandlerCompat
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaButton
import com.picacompose.ui.components.PicaCircularProgress
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaOutlinedButton
import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaTextField
import com.picacompose.ui.components.PicaToast
import com.picacompose.ui.theme.SkinTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.font.FontWeight

/**
 * 找回密码页：邮箱 → 密保问题 → 新密码。
 *
 * 官方流程不给你发邮件，答对密保问题就当场把新密码给出来，所以最后一步直接把新密码
 * 摆在屏幕上，顺手能复制，再回登录页填上。
 */
@Composable
fun ForgotPasswordScreen(
    controller: ForgotPasswordController,
    onBack: () -> Unit,
    onLoginWithNewPassword: (email: String, password: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    BackHandlerCompat(enabled = true, onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        PicaBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
                AuthTopBar(title = "找回密码", onBack = onBack)
                Spacer(Modifier.height(18.dp))
                when (controller.step) {
                    ForgotPasswordController.Step.Email -> EmailStep(
                        controller = controller,
                        onSubmit = { scope.launch { controller.submitEmail() } },
                    )

                    ForgotPasswordController.Step.Answer -> AnswerStep(
                        controller = controller,
                        onSubmit = { scope.launch { controller.submitAnswer() } },
                    )

                    ForgotPasswordController.Step.Done -> DoneStep(
                        controller = controller,
                        onLogin = onLoginWithNewPassword,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmailStep(controller: ForgotPasswordController, onSubmit: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = "用邮箱找回",
        style = MiuixTheme.textStyles.title1,
        color = colors.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "填注册时用的邮箱，我们把当时设的密保问题取回来。",
        style = MiuixTheme.textStyles.body1,
        color = colors.onSurfaceSecondary,
    )
    Spacer(Modifier.height(18.dp))
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.cardRadius),
        color = colors.tertiaryContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            PicaTextField(
                value = controller.email,
                onValueChange = controller::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !controller.loading,
                placeholder = "注册邮箱",
                singleLine = true,
                cornerRadius = 16.dp,
                leadingIcon = {
                    PicaGlyph(PicaIcon.Mail, Modifier.size(20.dp), colors.onSurfaceSecondary)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            AnimatedVisibility(visible = controller.error != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBanner(controller.error.orEmpty())
                }
            }
            Spacer(Modifier.height(20.dp))
            PicaButton(
                onClick = onSubmit,
                enabled = !controller.loading,
                cornerRadius = 999.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (controller.loading) {
                    PicaCircularProgress(
                        color = colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在取密保问题…", style = MiuixTheme.textStyles.button)
                } else {
                    Text("下一步", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

@Composable
private fun AnswerStep(controller: ForgotPasswordController, onSubmit: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = "回答密保问题",
        style = MiuixTheme.textStyles.title1,
        color = colors.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "选一个问题答对就能重置密码，答案要和注册时填的一模一样。",
        style = MiuixTheme.textStyles.body1,
        color = colors.onSurfaceSecondary,
    )
    Spacer(Modifier.height(16.dp))
    controller.questions.forEach { (no, text) ->
        QuestionOption(
            text = text,
            selected = controller.chosenNo == no,
            onClick = { controller.chooseQuestion(no) },
        )
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(8.dp))
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.cardRadius),
        color = colors.tertiaryContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            PicaTextField(
                value = controller.answer,
                onValueChange = controller::onAnswerChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !controller.loading,
                placeholder = "答案",
                singleLine = true,
                cornerRadius = 16.dp,
                leadingIcon = {
                    PicaGlyph(PicaIcon.Shield, Modifier.size(20.dp), colors.onSurfaceSecondary)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            AnimatedVisibility(visible = controller.error != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    AuthErrorBanner(controller.error.orEmpty())
                }
            }
            Spacer(Modifier.height(20.dp))
            PicaButton(
                onClick = onSubmit,
                enabled = !controller.loading,
                cornerRadius = 999.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (controller.loading) {
                    PicaCircularProgress(
                        color = colors.onPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在重置…", style = MiuixTheme.textStyles.button)
                } else {
                    Text("重置密码", style = MiuixTheme.textStyles.button)
                }
            }
            Spacer(Modifier.height(6.dp))
            PicaOutlinedButton(
                onClick = controller::backToEmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text("换个邮箱", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun DoneStep(
    controller: ForgotPasswordController,
    onLogin: (email: String, password: String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val password = controller.newPassword.orEmpty()
    Text(
        text = "新密码已重置",
        style = MiuixTheme.textStyles.title1,
        color = colors.onBackground,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "用下面的密码登录，进去之后可以在设置里改成自己好记的。",
        style = MiuixTheme.textStyles.body1,
        color = colors.onSurfaceSecondary,
    )
    Spacer(Modifier.height(18.dp))
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.cardRadius),
        color = colors.tertiaryContainer,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Text(
                text = "新密码",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = password,
                style = MiuixTheme.textStyles.title3,
                color = colors.onBackground,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            PicaButton(
                onClick = {
                    clipboard.setText(AnnotatedString(password))
                    PicaToast.show("新密码已复制")
                },
                cornerRadius = 999.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text("复制密码", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.height(20.dp))
            PicaButton(
                onClick = { onLogin(controller.email.trim(), password) },
                cornerRadius = 999.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text("用新密码登录", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 一条可以点的密保问题。 */
@Composable
private fun QuestionOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
        color = if (selected) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SkinTokens.rowRadius))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = 2.dp,
                        color = if (selected) colors.primary else colors.onSurfaceSecondary.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colors.primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = colors.onBackground,
            )
        }
    }
}
