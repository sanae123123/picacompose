package com.picacompose.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.BackHandlerCompat
import com.picacompose.ui.components.PicaBackdrop
import com.picacompose.ui.components.PicaButton
import com.picacompose.ui.components.PicaCircularProgress
import com.picacompose.ui.components.PicaSlidingChipRow
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.components.PicaTextField
import com.picacompose.ui.theme.SkinTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 注册页：官方的必填项一个不少（昵称、邮箱、密码、生日、性别、三个密保问题）。
 */
@Composable
fun RegisterScreen(
    controller: RegisterController,
    onBack: () -> Unit,
    onRegistered: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    BackHandlerCompat(enabled = true, onBack = onBack)
    val submit: () -> Unit = { scope.launch { controller.submit(onRegistered) } }

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
                AuthTopBar(title = "注册账号", onBack = onBack)
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "注册一个 PicACG 账号",
                    style = MiuixTheme.textStyles.title1,
                    color = colors.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "注册的是官方账号，以后也能在官方 App 登录。密保问题找回密码时要用，答案挑自己记得住的。",
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
                        LabeledField("昵称") {
                            PicaTextField(
                                value = controller.name,
                                onValueChange = controller::onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !controller.loading,
                                placeholder = "登录后显示的名字",
                                singleLine = true,
                                cornerRadius = 16.dp,
                                leadingIcon = {
                                    PicaGlyph(PicaIcon.Person, Modifier.size(20.dp), colors.onSurfaceSecondary)
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                        }
                        LabeledField("邮箱") {
                            PicaTextField(
                                value = controller.email,
                                onValueChange = controller::onEmailChange,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !controller.loading,
                                placeholder = "name@example.com",
                                singleLine = true,
                                cornerRadius = 16.dp,
                                leadingIcon = {
                                    PicaGlyph(PicaIcon.Mail, Modifier.size(20.dp), colors.onSurfaceSecondary)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                        }
                        LabeledField("密码") {
                            PicaTextField(
                                value = controller.password,
                                onValueChange = controller::onPasswordChange,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !controller.loading,
                                placeholder = "至少 8 位",
                                singleLine = true,
                                cornerRadius = 16.dp,
                                leadingIcon = {
                                    PicaGlyph(PicaIcon.Lock, Modifier.size(20.dp), colors.onSurfaceSecondary)
                                },
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
                                    imeAction = ImeAction.Next,
                                ),
                            )
                        }
                        LabeledField("生日") {
                            PicaTextField(
                                value = controller.birthday,
                                onValueChange = controller::onBirthdayChange,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !controller.loading,
                                placeholder = "1998-05-20",
                                singleLine = true,
                                cornerRadius = 16.dp,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                        }
                        LabeledField("性别") {
                            PicaSlidingChipRow(
                                options = listOf("男", "女", "机器人"),
                                selectedIndex = when (controller.gender) {
                                    "f" -> 1
                                    "bot" -> 2
                                    else -> 0
                                },
                                onSelect = { index ->
                                    controller.onGenderChange(
                                        when (index) {
                                            1 -> "f"
                                            2 -> "bot"
                                            else -> "m"
                                        },
                                    )
                                },
                                textStyle = MiuixTheme.textStyles.body1,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "密保问题（找回密码时用）",
                            style = MiuixTheme.textStyles.title4,
                            color = colors.onSurfaceSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        QuestionPair(
                            number = 1,
                            question = controller.question1,
                            onQuestionChange = controller::onQuestion1Change,
                            answer = controller.answer1,
                            onAnswerChange = controller::onAnswer1Change,
                            enabled = !controller.loading,
                        )
                        QuestionPair(
                            number = 2,
                            question = controller.question2,
                            onQuestionChange = controller::onQuestion2Change,
                            answer = controller.answer2,
                            onAnswerChange = controller::onAnswer2Change,
                            enabled = !controller.loading,
                        )
                        QuestionPair(
                            number = 3,
                            question = controller.question3,
                            onQuestionChange = controller::onQuestion3Change,
                            answer = controller.answer3,
                            onAnswerChange = controller::onAnswer3Change,
                            enabled = !controller.loading,
                            onSubmit = submit,
                        )
                        AnimatedVisibility(visible = controller.error != null) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                AuthErrorBanner(controller.error.orEmpty())
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        PicaButton(
                            onClick = submit,
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
                                Text("注册中…", style = MiuixTheme.textStyles.button)
                            } else {
                                Text("注册", style = MiuixTheme.textStyles.button)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "注册成功后回到登录页，用这个邮箱和密码登录即可。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 一行「标签 + 输入框」，标签压小放在上面。 */
@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(14.dp))
    }
}

/** 一组密保问题和答案。 */
@Composable
private fun QuestionPair(
    number: Int,
    question: String,
    onQuestionChange: (String) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    PicaTextField(
        value = question,
        onValueChange = onQuestionChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = "密保问题 $number，例如：我小学叫什么",
        singleLine = true,
        cornerRadius = 16.dp,
        leadingIcon = {
            PicaGlyph(PicaIcon.Shield, Modifier.size(20.dp), colors.onSurfaceSecondary)
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(8.dp))
    PicaTextField(
        value = answer,
        onValueChange = onAnswerChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = "答案 $number",
        singleLine = true,
        cornerRadius = 16.dp,
        keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = if (onSubmit != null) KeyboardActions(onDone = { onSubmit() }) else KeyboardActions.Default,
    )
    Spacer(Modifier.height(16.dp))
}
