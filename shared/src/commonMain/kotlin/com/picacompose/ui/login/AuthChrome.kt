package com.picacompose.ui.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaIconButton
import com.picacompose.ui.components.PicaSurface
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 登录 / 注册 / 找回密码共用的小标题栏：返回 + 标题。 */
@Composable
internal fun AuthTopBar(title: String, onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        PicaIconButton(onClick = onBack) {
            PicaGlyph(PicaIcon.Back, Modifier.size(22.dp), colors.onBackground)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.title4,
            color = colors.onBackground,
        )
    }
}

/** 表单里的报错条，和登录页那条长得一样。 */
@Composable
internal fun AuthErrorBanner(message: String) {
    val colors = MiuixTheme.colorScheme
    PicaSurface(
        shape = RoundedCornerShape(SkinTokens.rowRadius),
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
