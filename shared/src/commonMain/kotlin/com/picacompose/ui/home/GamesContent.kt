package com.picacompose.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.data.pica
import com.picacompose.picaapi.model.GameSummary

/** 游戏页：先按接口列标题，下载和详情后面再说。 */
@Composable
fun GamesContent() {
    val colors = MaterialTheme.colorScheme
    var reload by remember { mutableStateOf(0) }
    var games by remember { mutableStateOf<List<GameSummary>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        games = null
        error = null
        runCatching { pica.games.list(page = 1).docs }
            .onSuccess { games = it }
            .onFailure { error = it.message ?: "游戏列表没拉到" }
    }

    PageScaffold {
        PageHeader(title = "游戏", subtitle = "PicACG 站内的小游戏。")
        Spacer(Modifier.height(18.dp))
        when {
            error != null -> FailedBlock(error.orEmpty()) { reload++ }
            games == null -> LoadingBlock()
            games.orEmpty().isEmpty() -> EmptyBlock("接口没返回游戏")
            else -> games.orEmpty().forEach { game ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colors.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = listOfNotNull(
                                game.publisher,
                                game.version?.let { "v$it" },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        if (game.android) InfoChip("安卓")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
