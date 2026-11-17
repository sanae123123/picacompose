package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.picacompose.data.ExportFormat
import com.picacompose.ui.components.PicaAlertDialog
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.PicaTextButton
import com.picacompose.ui.components.rememberDialogPop
import com.picacompose.ui.theme.SkinTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 导出格式选择：点一个就直接拉系统保存对话框。 */
@Composable
internal fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onPick: (ExportFormat) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val pop = rememberDialogPop(onDismiss)

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text(text = "导出这本", style = MiuixTheme.textStyles.title4) },
        text = {
            Column {
                Text(
                    text = "选个格式，接着挑保存位置。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(6.dp))
                for (format in ExportFormat.entries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SkinTokens.rowRadius))
                            .clickable { onPick(format) }
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PicaGlyph(PicaIcon.Export, Modifier.size(20.dp), colors.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = format.label,
                                style = MiuixTheme.textStyles.subtitle,
                                color = colors.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = format.hint,
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            PicaTextButton(onClick = pop::dismiss) { Text("取消") }
        },
    )
}
