package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import com.picacompose.data.ExportProgress
import com.picacompose.ui.components.PicaAlertDialog
import com.picacompose.ui.components.PicaLinearProgress
import com.picacompose.ui.components.PicaTextButton
import com.picacompose.ui.components.rememberDialogPop
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 导出的前台弹窗：进度条 + 一句人话。
 *
 * 进行中能点「后台执行」（收起来、进度挪到通知栏）或「取消」；
 * 完成/失败变成「知道了」。
 */
@Composable
internal fun ExportProgressDialog(
    progress: ExportProgress,
    onBackground: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val done = progress.finished || progress.error != null
    val pop = rememberDialogPop(onDismiss)

    PicaAlertDialog(
        onDismissRequest = { if (done) pop.dismiss() },
        modifier = pop.modifier,
        title = {
            Text(
                text = when {
                    progress.error != null -> "导出失败"
                    progress.finished -> "导出完成"
                    else -> "正在导出"
                },
                style = MiuixTheme.textStyles.title4,
            )
        },
        text = {
            Column {
                Text(
                    text = progress.title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${progress.formatLabel} · ${progress.detail}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(10.dp))
                PicaLinearProgress(
                    progress = if (done) 1f else progress.fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
                if (progress.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = progress.error,
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.error,
                    )
                }
            }
        },
        confirmButton = {
            if (done) {
                PicaTextButton(onClick = { pop.dismiss() }) { Text("知道了") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PicaTextButton(onClick = onCancel) { Text("取消") }
                    PicaTextButton(onClick = onBackground) { Text("后台执行") }
                }
            }
        },
    )
}
