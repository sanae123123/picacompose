package com.picacompose.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.UpdateChecker
import com.picacompose.data.UpdateInfo
import com.picacompose.data.UpdateResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 检查更新的界面侧状态。
 *
 * 冷启动由 [UpdatePromptHost] 自动查一次（三小时内不重复查）；设置页的「检查更新」
 * 调 [checkNow]，同一个弹窗也会自己弹出来。
 */
object UpdatePrompt {

    internal var info by mutableStateOf<UpdateInfo?>(null)

    /** 手动检查：有新版本就弹窗，没有就返回 `Ok(null)`，设置页据此提示「已是最新」。 */
    suspend fun checkNow(): UpdateResult {
        val result = UpdateChecker.checkNow()
        if (result is UpdateResult.Ok && result.info != null) info = result.info
        return result
    }

    /** 冷启动那次：失败什么都不做，别在启动时弹「连不上 GitHub」。 */
    internal suspend fun checkAutomatically() {
        val result = UpdateChecker.checkIfDue() ?: return
        if (result !is UpdateResult.Ok) return
        val found = result.info ?: return
        if (found.version != UpdateChecker.dismissedVersion) info = found
    }

    internal fun dismiss(info: UpdateInfo) {
        UpdateChecker.dismiss(info.version)
        this.info = null
    }
}

/** 挂在 App 根部的检查更新弹窗。 */
@Composable
fun UpdatePromptHost() {
    val info = UpdatePrompt.info
    val openBrowser = rememberBrowserOpener()

    LaunchedEffect(Unit) { UpdatePrompt.checkAutomatically() }

    if (info == null) return
    val pop = rememberDialogPop(onDismiss = { UpdatePrompt.dismiss(info) })

    PicaAlertDialog(
        onDismissRequest = pop::dismiss,
        modifier = pop.modifier,
        title = { Text("发现新版本 ${info.version}") },
        text = {
            Text(
                text = info.notes.ifBlank { "有新版本可以更新了。" },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            PicaTextButton(onClick = {
                openBrowser(info.downloadUrl)
                pop.dismiss()
            }) {
                Text("去下载")
            }
        },
        dismissButton = {
            PicaTextButton(onClick = pop::dismiss) { Text("以后再说") }
        },
    )
}
