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
    val totalViews: Int = 0,
    val totalLikes: Int = 0,
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
    @SerialName("_creator") val creator: Creator? = null,
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
    val totalLikes: Int = 0,
    val totalViews: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** 章节。 */
@Serializable
data class Episode(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val order: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
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
    @SerialName("activation_date") val activationDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Comment(
    @SerialName("_id") val id: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val likesCount: Int = 0,
    @SerialName("commentsCount") val childsCount: Int = 0,
    val isLiked: Boolean = false,
    val isTop: Boolean = false,
    val hide: Boolean = false,
    @SerialName("_user") val user: UserBasic? = null,
    @SerialName("_comic") val comicId: String? = null,
    @SerialName("_game") val gameId: String? = null,
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
    val punchInLastDay: String? = null,
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

// ---- 补全的接口模型（参考 Bika 客户端 core/network） ----

/** `GET collections`：首页合集，每个分组里是一串漫画。 */
@Serializable
data class CollectionsData(val collections: List<CollectionGroup> = emptyList())

@Serializable
data class CollectionGroup(val comics: List<ComicSummary> = emptyList())

/** `GET comics/knight-leaderboard`：骑士榜（贡献者）。 */
@Serializable
data class KnightLeaderboardData(val users: List<UserBasic> = emptyList())

/** `POST comics/advanced-search` 请求体。`sort` 同列表接口：`dd` / `da` / `ld` / `vd`。 */
@Serializable
data class AdvancedSearchRequest(
    val keyword: String = "",
    val sort: String? = null,
    val categories: List<String> = emptyList(),
)

/**
 * `GET users/my-comments`：自己发过的评论。
 * 这里的 `_comic` / `_game` 是对象（带标题），跟漫画评论区的字符串 id 不一样，
 * 所以单独建模型，免得反序列化炸掉。
 */
@Serializable
data class MyCommentsData(val comments: Paged<MyComment> = Paged())

@Serializable
data class MyComment(
    @SerialName("_id") val id: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val likesCount: Int = 0,
    @SerialName("commentsCount") val childsCount: Int = 0,
    val isLiked: Boolean = false,
    val hide: Boolean = false,
    @SerialName("_comic") val comic: CommentTarget? = null,
    @SerialName("_game") val game: CommentTarget? = null,
)

/** 评论所属对象的最小信息（`_comic` / `_game` 字段）。 */
@Serializable
data class CommentTarget(
    @SerialName("_id") val id: String = "",
    val title: String = "",
)

/** `GET users/notifications`。 */
@Serializable
data class NotificationsData(val notifications: Paged<NotificationDoc> = Paged())

@Serializable
data class NotificationDoc(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val content: String = "",
    val cover: Thumbnail? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("_redirectId") val redirectId: String? = null,
    val redirectType: String? = null,
    @SerialName("_sender") val sender: NotificationSender? = null,
    val system: Boolean = false,
    @SerialName("_user") val userId: String? = null,
)

@Serializable
data class NotificationSender(
    @SerialName("_id") val id: String = "",
    val name: String = "",
    val avatar: Thumbnail? = null,
    val level: Int = 0,
    val role: String? = null,
    val title: String? = null,
    val slogan: String? = null,
    val gender: String? = null,
    val character: String? = null,
)

/** `POST auth/register` 请求体。字段与服务端一一对应，密保问题三组都得填。 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val birthday: String,
    val gender: String,
    val question1: String,
    val answer1: String,
    val question2: String,
    val answer2: String,
    val question3: String,
    val answer3: String,
)

/** `PUT users/profile` 请求体（改签名）。 */
@Serializable
data class SloganRequest(val slogan: String)

/** `PUT users/password` 请求体。注意旧密码的字段名是 `old_password`。 */
@Serializable
data class ChangePasswordRequest(
    @SerialName("old_password") val oldPassword: String,
    val password: String,
)

// ---- 按官方 App 逆向文档（55 端点）补齐的模型 ----

/** `POST auth/forgot-password` 返回的三个密保问题（用于后续 reset）。 */
@Serializable
data class ForgotPasswordData(
    val question1: String? = null,
    val question2: String? = null,
    val question3: String? = null,
)

/** `POST auth/reset-password` 返回重置后的新密码。 */
@Serializable
data class ResetPasswordData(val password: String? = null)

/** `GET chat` 聊天室入口列表（消息本身走 socket.io）。 */
@Serializable
data class ChatData(val chatList: List<ChatRoom> = emptyList())

@Serializable
data class ChatRoom(
    val title: String = "",
    val description: String? = null,
    val url: String = "",
    val avatar: String? = null,
)

/** `GET pica-apps` 官方推荐的小应用。 */
@Serializable
data class PicaAppsData(val apps: List<PicaApp> = emptyList())

@Serializable
data class PicaApp(
    val title: String = "",
    val url: String = "",
    val icon: String? = null,
    val showTitleBar: Boolean = false,
    val description: String? = null,
)

/** `GET announcements` 公告。 */
@Serializable
data class AnnouncementsData(val announcements: Paged<Announcement> = Paged())

@Serializable
data class Announcement(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val content: String = "",
    val thumb: Thumbnail? = null,
)

/** `GET applications?platform=android` 版本更新日志。 */
@Serializable
data class ApplicationsData(val applications: Paged<ApplicationDoc> = Paged())

@Serializable
data class ApplicationDoc(
    @SerialName("_id") val id: String = "",
    val version: String = "",
    val updateContent: String? = null,
    val downloadUrl: String? = null,
    val apk: Thumbnail? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** 游戏列表项（apps 区）。 */
@Serializable
data class GameSummary(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val version: String? = null,
    val publisher: String? = null,
    val suggest: Boolean = false,
    val adult: Boolean = false,
    val android: Boolean = false,
    val ios: Boolean = false,
    val picaLink: Boolean = false,
    val icon: Thumbnail? = null,
    val androidLinks: List<String> = emptyList(),
    val iosLinks: List<String> = emptyList(),
)

@Serializable
data class GameDetail(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val description: String? = null,
    val version: String? = null,
    val publisher: String? = null,
    val icon: Thumbnail? = null,
    val screenshots: List<Thumbnail> = emptyList(),
    val adult: Boolean = false,
    val suggest: Boolean = false,
    val android: Boolean = false,
    val ios: Boolean = false,
    val picaLink: Boolean = false,
    val androidLinks: List<String> = emptyList(),
    val iosLinks: List<String> = emptyList(),
    val androidSize: Long = 0,
    val iosSize: Long = 0,
    val downloadsCount: Int = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLiked: Boolean = false,
    val updateContent: String? = null,
    val videoLink: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GameListData(val games: Paged<GameSummary> = Paged())

@Serializable
data class GameDetailData(val game: GameDetail = GameDetail())

/** 只回一句 message 的接口（隐藏/举报评论等）。 */
@Serializable
data class MessageData(val message: String? = null)

/** `POST comments/{id}/top`。 */
@Serializable
data class TopCommentData(val isTop: Boolean = false)

/** `POST users/{id}/dirty`。 */
@Serializable
data class DirtyData(val dirty: Boolean = false)

/** `PUT users/avatar`。 */
@Serializable
data class AvatarData(val avatar: Thumbnail? = null)

// ---- 请求体（字段名取自官方 App 的 Gson 模型，未混淆） ----

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val questionNo: Int,
    val answer: String,
)

@Serializable
data class UpdatePicaIdRequest(val email: String, val name: String)

@Serializable
data class UpdateQandARequest(
    val question1: String,
    val answer1: String,
    val question2: String,
    val answer2: String,
    val question3: String,
    val answer3: String,
)

@Serializable
data class TitleRequest(val title: String)

@Serializable
data class AvatarRequest(val avatar: String)

@Serializable
data class UserIdRequest(val userId: String)

@Serializable
data class AdjustExpRequest(val userId: String, val exp: Int)
