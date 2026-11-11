package com.picacompose.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.picacompose.picaapi.model.Comment
import com.picacompose.picaapi.model.MyComment
import com.picacompose.picaapi.model.UserBasic
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 历史评论：`GET users/my-comments`，我发过的评论，按时间从新到旧。
 *
 * 每条就是详情页那一套 [CommentRow]：点卡片主体就地展开楼中楼（按需拉
 * `comments/{id}/childrens`），再点收起。卡片上方一行是它所在的本子，点进去看原帖。
 */
@Composable
fun MyCommentsContent(
    pager: Pager<MyComment>,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onOpenComic: (comicId: String, title: String, key: String) -> Unit = { _, _, _ -> },
    commentRowModifier: @Composable (String) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
    /** 当前账号的昵称，评论行显示的名字（接口不返回作者，只能拿自己的）。 */
    selfName: String? = null,
) {
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme

    LaunchedEffect(pager) {
        if (pager.items.isEmpty()) pager.loadNext()
    }

    LaunchedEffect(listState, pager) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 4 && pager.hasMore && pager.error == null) {
                    pager.loadNext()
                }
            }
    }

    PageScaffold(
        state = listState,
        modifier = modifier,
        onRefresh = { pager.reloadFirstPage() },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    PicaGlyph(
                        icon = PicaIcon.Back,
                        modifier = Modifier.size(22.dp),
                        tint = colors.onBackground,
                    )
                }
                Spacer(Modifier.width(6.dp))
                PageHeader(
                    title = "历史评论",
                    action = if (pager.total > 0) {
                        {
                            Text(
                                text = "共 ${pager.total} 条",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceSecondary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        if (pager.items.isEmpty()) {
            item {
                when {
                    pager.error != null -> FailedBlock(pager.error.orEmpty()) {
                        scope.launch { pager.retry() }
                    }

                    pager.loading -> LoadingBlock("正在拉评论…")
                    else -> EmptyBlock("还没发过评论")
                }
            }
        }
        items(pager.items, key = { it.id }) { comment ->
            val key = "myc-${comment.id}"
            MyCommentCard(
                comment = comment,
                selfName = selfName,
                modifier = commentRowModifier(key),
                onOpenComic = { comicId, title -> onOpenComic(comicId, title, key) },
            )
        }
        if (pager.loading && pager.items.isNotEmpty()) {
            item { LoadingBlock("再拉一页…") }
        }
    }
}

/**
 * 一条历史评论：所在本子的标题 + 评论本体。
 *
 * 评论本体直接用 [CommentRow]，楼中楼的展开、点赞都跟详情页一致；
 * `MyComment` 里没有作者信息（本来就是自己发的），所以头像那一列不显示、名字用当前账号的昵称。
 */
@Composable
private fun MyCommentCard(
    comment: MyComment,
    selfName: String?,
    onOpenComic: (comicId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val comic = comment.comic
    val target = comic?.title?.takeIf { it.isNotBlank() }
        ?: comment.game?.title?.takeIf { it.isNotBlank() }
    val openable = comic != null && comic.id.isNotBlank()

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = openable) {
                    if (openable) onOpenComic(comic.id, comic.title.orEmpty())
                }
                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = target ?: "未知本子",
                style = MiuixTheme.textStyles.button,
                color = if (openable) colors.primary else colors.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (openable) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "看本子",
                    style = MiuixTheme.textStyles.footnote2,
                    color = colors.onSurfaceSecondary,
                )
                PicaGlyph(
                    icon = PicaIcon.ArrowRight,
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceSecondary,
                )
            }
            if (comment.hide) {
                Spacer(Modifier.width(8.dp))
                InfoChip("已隐藏")
            }
        }
        CommentRow(
            // 名字用当前账号的昵称；头像不显示（历史评论接口不带作者，加载不出来只会剩个空框）。
            comment = comment.asComment(selfName),
            onReply = null,
            showAvatar = false,
        )
    }
}

/** `MyComment` → 评论行要的那份 `Comment`，字段一一对上，作者名用当前账号的昵称。 */
private fun MyComment.asComment(selfName: String?): Comment = Comment(
    id = id,
    content = content,
    createdAt = createdAt,
    likesCount = likesCount,
    childsCount = childsCount,
    isLiked = isLiked,
    hide = hide,
    user = UserBasic(name = selfName?.takeIf { it.isNotBlank() } ?: "绅士"),
)
