package com.picacompose.picaapi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 通用信封：`{ code, message, data }`。 */
@Serializable
data class Envelope<T>(
    val code: Int = 0,
    val message: String? = null,
    val data: T? = null,
)

/** 分页数据。 */
@Serializable
data class Paged<T>(
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val pages: Int = 0,
    val docs: List<T> = emptyList(),
)

/** 图片 / 缩略图资源。 */
@Serializable
data class Thumbnail(
    val fileServer: String? = null,
    val path: String? = null,
    val originalName: String? = null,
)

/** 漫画列表项。 */
@Serializable
data class ComicSummary(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val author: String? = null,
    val likesCount: Int = 0,
    val pagesCount: Int = 0,
    @SerialName("epsCount") val episodesCount: Int = 0,
    val finished: Boolean = false,
    val categories: List<String> = emptyList(),
    val thumb: Thumbnail? = null,
    val viewsCount: Int = 0,
    val leaderboardCount: Int = 0,
)

@Serializable
data class Creator(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val avatar: Thumbnail? = null,
    val exp: Int = 0,
    val level: Int = 0,
    val role: String? = null,
    val title: String? = null,
    val verified: Boolean = false,
)

/** 漫画详情。 */
@Serializable
data class ComicDetail(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val author: String? = null,
    val description: String? = null,
    val chineseTeam: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val thumb: Thumbnail? = null,
    val creator: Creator? = null,
    @SerialName("epsCount") val episodesCount: Int = 0,
    val pagesCount: Int = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val viewsCount: Int = 0,
    val finished: Boolean = false,
    val isFavourite: Boolean = false,
    val isLiked: Boolean = false,
    val allowComment: Boolean = true,
    val allowDownload: Boolean = true,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** 章节。 */
@Serializable
data class Episode(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val order: Int = 0,
    val updatedAt: String? = null,
    val status: Int = 0,
    val selected: Boolean = false,
)

/** 漫画单页。 */
@Serializable
data class ComicPage(
    @SerialName("_id") val id: String = "",
    val media: Thumbnail? = null,
)

@Serializable
data class Category(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val description: String? = null,
    val thumb: Thumbnail? = null,
    val link: String? = null,
)

@Serializable
data class Banner(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val type: String? = null,
    val shortDescription: String? = null,
    val thumb: Thumbnail? = null,
    val link: String? = null,
    @SerialName("_comic") val comicId: String? = null,
    @SerialName("_game") val gameId: String? = null,
)

@Serializable
data class UserBasic(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val avatar: Thumbnail? = null,
    val level: Int = 0,
    val exp: Int = 0,
    val character: String? = null,
    val gender: String? = null,
    val verified: Boolean = false,
    val title: String? = null,
)

@Serializable
data class UserProfile(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val email: String? = null,
    val avatar: Thumbnail? = null,
    val level: Int = 0,
    val exp: Int = 0,
    val title: String? = null,
    val slogan: String? = null,
    val character: String? = null,
    val characters: List<String> = emptyList(),
    val birthday: String? = null,
    val gender: String? = null,
    val role: String? = null,
    val verified: Boolean = false,
    val isPunched: Boolean = false,
    val activationDate: String? = null,
)

@Serializable
data class Comment(
    @SerialName("_id") val id: String = "",
    val content: String = "",
    val createdAt: String? = null,
    val likesCount: Int = 0,
    val childsCount: Int = 0,
    val isLiked: Boolean = false,
    val isTop: Boolean = false,
    val hide: Boolean = false,
    val user: UserBasic? = null,
    val comicId: String? = null,
    val gameId: String? = null,
)

/** `init` 接口返回。 */
@Serializable
data class InitData(
    val categories: List<Category> = emptyList(),
    val imageServer: String? = null,
    val isPunched: Boolean = false,
    val isIdUpdated: Boolean = false,
)

// ---- 各类响应的 data 包装（服务端会把真正的数据再包一层） ----

@Serializable
data class ComicListData(val comics: Paged<ComicSummary> = Paged())

@Serializable
data class ComicDetailData(val comic: ComicDetail = ComicDetail())

@Serializable
data class EpisodeListData(val eps: Paged<Episode> = Paged())

@Serializable
data class ComicPagesData(
    val pages: Paged<ComicPage> = Paged(),
    val ep: Episode? = null,
)

@Serializable
data class CommentListData(
    val comments: Paged<Comment> = Paged(),
    val topComments: List<Comment> = emptyList(),
)

@Serializable
data class ComicRandomData(val comics: List<ComicSummary> = emptyList())

@Serializable
data class SignInData(val token: String = "")

@Serializable
data class UserProfileData(val user: UserProfile = UserProfile())

@Serializable
data class PunchInData(
    val res: PunchInResult? = null,
)

@Serializable
data class PunchInResult(
    val status: String? = null,
)

@Serializable
data class ActionData(val action: String? = null)

@Serializable
data class KeywordData(val keywords: List<String> = emptyList())

// ---- 请求体 ----

@Serializable
data class SignInRequest(val email: String, val password: String)

@Serializable
data class CommentRequest(val content: String)
