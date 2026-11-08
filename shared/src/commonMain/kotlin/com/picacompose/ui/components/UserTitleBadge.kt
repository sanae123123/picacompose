package com.picacompose.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 昵称旁边的称号小徽章（「萌新」「绅士」这类）。
 *
 * 没有称号就什么都不画，调用方不用自己判空。
 */
@Composable
fun UserTitleBadge(title: String?, modifier: Modifier = Modifier) {
    val text = title?.trim().orEmpty()
    if (text.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
