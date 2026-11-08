package com.picacompose.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.Announcement

/** 公告弹窗：拉第一页公告，标题 + 正文，整块能滚。 */
@Composable
fun AnnouncementsDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var announcements by remember { mutableStateOf<List<Announcement>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { pica.meta.announcements(page = 1).docs }
            .onSuccess { announcements = it }
            .onFailure { error = it.message ?: "公告没拉到" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("公告", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val list = announcements
                when {
                    error != null -> Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )

                    list == null -> LoadingBlock("正在拉公告…")
                    list.isEmpty() -> Text(
                        text = "暂时没有公告。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )

                    else -> list.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = colors.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                        )
                        val content = item.content.trim()
                        if (content.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
