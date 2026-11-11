package com.picacompose.ui.home

import com.picacompose.ui.components.PicaSwitch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import com.picacompose.ui.components.PicaFilterChip
import com.picacompose.ui.theme.AppSkin
import com.picacompose.ui.theme.SkinState
import com.picacompose.ui.components.PicaDropdownMenu
import com.picacompose.ui.components.PicaDropdownMenuItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.picacompose.ui.components.PicaOutlinedButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.picacompose.data.APP_VERSION
import com.picacompose.data.ImageIpsStatus
import com.picacompose.data.LinePreferences
import com.picacompose.data.LineRouter
import com.picacompose.data.REPO_URL
import com.picacompose.data.RouteStore
import com.picacompose.data.SessionStore
import com.picacompose.data.clearImageCache
import com.picacompose.data.formatBytes
import com.picacompose.data.imageCacheBytes
import com.picacompose.data.pica
import com.picacompose.picaapi.ImageIps
import kotlinx.coroutines.launch
import com.picacompose.ui.components.PicaGlyph
import com.picacompose.ui.components.PicaIcon
import com.picacompose.ui.components.rememberBrowserOpener

/**
 * 设置：账号、线路开关、图片、版本，最后是退出登录。
 *
 * 下面这些说明原来堆在页面底部（一般人不会往下翻），现在挪到这里，改代码的时候就近能看到：
 *
 * - 记住账号：只保存 token 和账号名，不存密码。开着时下次打开直接恢复会话；
 *   关掉会把已经存的 token 一起清掉，下次得重新登录。退出登录只清 token、账号名留着。
 * - 线路的开关（自动测速 / 快速进入）改完立即生效（关掉自动测速会当场停掉正在跑的测速），
 *   不会再自己换线路，网络变了要手动去线路页重选。快速进入关掉后每次启动会多等一次测速。
 * - DoH：打开后接口走 DoH 解析，图片不走（图片直接钉 Cloudflare 地址，更省事）。
 *   一个可信地址都拿不到时会自动关掉开关并退回系统 DNS，设置页的开关也会跟着变回关；
 *   单个端点挂掉会在几路端点之间自动换到还能用的那一路。
 * - 图片：地址优先用 /init 下发的图床。图床域名（storage1 / storage-b / s3.picacomic.com）
 *   在很多网络上会被系统解析成别的公司的 IP，所以图片请求单独钉了一组 Cloudflare 地址，
 *   系统解析的结果排在后面兜底。启动后在后台把候选地址挨个测一遍，按握手耗时挑最快的几台，
 *   测速范围是 Cloudflare 官方公布的整套地址段（均匀取样几百台一起测），500ms 以内按延迟取最好的 10 台，
 *   之后每 30 分钟重测；冷启动时先用 /init 下发的官方源，优选测完再顶到前面。
 *   接口域名也在 Cloudflare 后面，所以同一份顺序也管接口 —— 优选出来的地址排前面，
 *   线路钉住的 IP 跟着兜底。候选里带 IPv6 地址，设备有 v6 出口就会一起测、一起用；
 *   全测不出来就退回官方池和内置种子地址，不会把能用的清空。
 *   「图片 IP 优选」可以整个关掉：关了就不再后台测速，只用 /init 下发的地址加内置种子。
 *   只要手上有优选池，就不会再自动测线路、也不会自己换线路；请求连不上时先在池子里换一台地址，
 *   连着换三台都不行才重新测一遍线路。
 * - 图片质量：对应官方那套 `image-quality` 请求头（original / low / medium / high），
 *   图床按它决定返回的尺寸；原图最清楚也最费流量，默认「中」和官方一致。
 * - 清除缓存：只清 Coil 的图片缓存（内存 + 磁盘），不动登录态、线路和阅读进度。
 */
@Composable
fun SettingsContent(onSignOut: () -> Unit) {
    val session = remember { SessionStore() }
    var savedAccount by remember { mutableStateOf(session.savedAccount) }
    var rememberOn by remember { mutableStateOf(session.remember) }
    val scope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current
    // 缓存大小算一次放这儿：清完刷新，不然得退出去再进来才变。
    var cacheBytes by remember { mutableStateOf(-1L) }
    LaunchedEffect(platformContext) { cacheBytes = imageCacheBytes(platformContext) }
    var clearing by remember { mutableStateOf(false) }
    val route = remember { RouteStore() }
    val preferences = remember { LinePreferences() }
    var qualityIndex by remember { mutableStateOf(preferences.imageQuality) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    val openBrowser = rememberBrowserOpener()
    val line = route.savedLine
    val latency = route.savedLatencyMs
    val ipCount = route.savedIps.size
    // 开关的界面状态：改完立刻落盘，下次冷启动按新的来。
    var autoProbe by remember { mutableStateOf(preferences.autoProbe) }
    var fastEntry by remember { mutableStateOf(preferences.fastEntry) }
    var doh by remember { mutableStateOf(preferences.doh) }
    var useImageIps by remember { mutableStateOf(preferences.imageIps) }
    // 「几分钟前」要自己走：没有系统时钟依赖，用单调时间算相对时长。
    val imageIpsAgo by produceState(-1L) {
        while (true) {
            value = ImageIpsStatus.finishedAt?.elapsedNow()?.inWholeSeconds ?: -1L
            delay(10_000)
        }
    }

    PageScaffold(
        header = { PageHeader(title = "设置") },
    ) {
        item {
            PicaCard {
                DetailRow(icon = PicaIcon.Star, title = "皮肤", value = SkinState.current.label)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppSkin.entries.forEach { skin ->
                        PicaFilterChip(
                            selected = SkinState.current == skin,
                            onClick = { SkinState.select(skin) },
                        ) {
                            Text(
                                text = skin.label,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                SwitchRow(
                    icon = PicaIcon.Person,
                    title = "记住账号",
                    summary = "只存令牌和账号名，不存密码。关掉会清掉已存的令牌，下次打开要重新登录。",
                    checked = rememberOn,
                    onCheckedChange = { on ->
                        rememberOn = on
                        session.remember = on
                        // 刚关掉的话，本机已经没令牌了，账号名也一起忘掉。
                        savedAccount = session.savedAccount
                        // 开着的时候要是这次登录还没落盘（比如登录时没勾），现在就补上。
                        if (on) {
                            val token = pica.token.value
                            if (!token.isNullOrBlank()) {
                                session.save(token, savedAccount.orEmpty())
                                savedAccount = session.savedAccount
                            }
                        }
                    },
                )
                DetailRow(
                    icon = PicaIcon.Lock,
                    title = "保存的账号",
                    value = savedAccount ?: "还没有",
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                SwitchRow(
                    icon = PicaIcon.Speed,
                    title = "自动测速",
                    summary = "启动时测一遍线路，挑最快的用；关掉就用上次的线路，不测也不自动换" +
                        "（当场就会停掉正在跑的测速，不用重启）。",
                    checked = autoProbe,
                    onCheckedChange = { on ->
                        autoProbe = on
                        preferences.autoProbe = on
                        // 关掉之后别再让后台测速跑完顺手把线路切了。
                        if (!on) LineRouter.stop()
                    },
                )
                // 子开关：父开关关掉时置灰，但值留着，父开关开回来还是原来的选择。
                SwitchRow(
                    icon = PicaIcon.Bolt,
                    title = "快速进入并后台测速",
                    summary = "先按上次的线路进主界面，测速在后台跑；关掉就停在线路选择页，等测完再进。",
                    checked = fastEntry,
                    enabled = autoProbe,
                    indent = true,
                    onCheckedChange = { on ->
                        fastEntry = on
                        preferences.fastEntry = on
                    },
                )
                Spacer(Modifier.height(6.dp))
                DetailRow(
                    icon = PicaIcon.ArrowRight,
                    title = "线路",
                    value = line?.let { "${it.title} · 通道 ${it.channel}" } ?: "还没选",
                )
                DetailRow(
                    icon = PicaIcon.Gear,
                    title = "测速",
                    value = when {
                        !autoProbe -> "自动测速已关"
                        latency != null -> "$latency ms"
                        ipCount > 0 -> "$ipCount 个下发 IP"
                        else -> "系统解析"
                    },
                )
                // 后台测速列表：换完线路会重测一遍，这里就是最近一次的结果，不通的明确标出来。
                LineRouter.statuses.forEach { status ->
                    DetailRow(
                        icon = PicaIcon.Bolt,
                        title = status.line.title,
                        value = status.latencyMs?.let { "$it ms" } ?: "不通",
                    )
                }
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    icon = PicaIcon.Shield,
                    title = "DoH 解析",
                    summary = "用 DNS over HTTPS 解析所有域名（接口、图片），绕开被污染的 DNS。" +
                        "默认关；打了之后每次启动多问一次 DoH（和 /init 并行，不会多等一个来回）。" +
                        "解析不通会自动关掉这个开关，不会让应用变成连不上。",
                    checked = doh,
                    onCheckedChange = { on ->
                        doh = on
                        preferences.doh = on
                    },
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    icon = PicaIcon.Speed,
                    title = "图片 IP 优选",
                    summary = "后台把 Cloudflare 官方地址段取样测一遍，500ms 以内的按延迟取最好的 10 台，" +
                        "图片和接口轮着用；关掉只用 /init 下发的地址。开关立即生效，不用重启。",
                    checked = useImageIps,
                    onCheckedChange = { on ->
                        useImageIps = on
                        preferences.imageIps = on
                        ImageIps.updateEnabled(on)
                        if (!on) {
                            ImageIps.preferOfficialFirst()
                            ImageIpsStatus.off()
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
                // 画质：官方也是每张图带 image-quality 头，图床按档位返回不同尺寸。
                Box {
                    PickerRow(
                        icon = PicaIcon.Star,
                        title = "图片质量",
                        value = imageQualityLabel(qualityIndex),
                        onClick = { qualityMenuOpen = true },
                    )
                    PicaDropdownMenu(
                        expanded = qualityMenuOpen,
                        onDismissRequest = { qualityMenuOpen = false },
                    ) {
                        IMAGE_QUALITY_CHOICES.forEach { (index, label) ->
                            PicaDropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    qualityMenuOpen = false
                                    qualityIndex = index
                                    preferences.imageQuality = index
                                    // 偏好 setter 会同步 ImageQualitySetting，下一张图就按新档位来。
                                },
                            )
                        }
                    }
                }
                PickerRow(
                    icon = PicaIcon.Close,
                    title = "清除图片缓存",
                    value = if (clearing) "清理中…" else formatBytes(cacheBytes),
                    onClick = {
                        if (!clearing) {
                            scope.launch {
                                clearing = true
                                cacheBytes = clearImageCache(platformContext)
                                clearing = false
                            }
                        }
                    },
                )
                Spacer(Modifier.height(6.dp))
                DetailRow(
                    icon = PicaIcon.Gear,
                    title = "优选状态",
                    value = imageIpsPhaseText(),
                )
                DetailRow(
                    icon = PicaIcon.Bolt,
                    title = "地址池",
                    value = imageIpsPoolText(),
                )
                DetailRow(
                    icon = PicaIcon.Speed,
                    title = "上次完成",
                    value = imageIpsAgoText(imageIpsAgo),
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaCard {
                DetailRow(PicaIcon.Grid, "主题", "Material You 基线配色")
                DetailRow(PicaIcon.Gear, "版本", APP_VERSION)
                LinkRow(
                    icon = PicaIcon.Star,
                    title = "GitHub",
                    url = REPO_URL,
                    onClick = { openBrowser(REPO_URL) },
                )
                DetailRow(PicaIcon.ArrowRight, "接口", "picaapi 模块")
            }
            Spacer(Modifier.height(14.dp))
        }
        item {
            PicaOutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.height(52.dp).fillMaxWidth(),
            ) {
                PicaGlyph(PicaIcon.Logout, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出登录", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 画质菜单项：索引对应 [ImageQuality]，从最清楚到最省流量排。 */
private val IMAGE_QUALITY_CHOICES = listOf(
    0 to "原图",
    3 to "高",
    2 to "中",
    1 to "低",
)

/** 画质索引 → 界面文案。 */
private fun imageQualityLabel(index: Int): String =
    IMAGE_QUALITY_CHOICES.firstOrNull { it.first == index }?.second ?: "中"

/** 后台优选现在的状态。 */
private fun imageIpsPhaseText(): String = when (ImageIpsStatus.phase) {
    ImageIpsStatus.Phase.Off -> "已关闭"
    ImageIpsStatus.Phase.Idle -> "准备中"
    ImageIpsStatus.Phase.Scanning -> "测速中 ${ImageIpsStatus.tested}/${ImageIpsStatus.total}"
    ImageIpsStatus.Phase.Done -> "已完成"
    ImageIpsStatus.Phase.Failed -> "这轮没测出可用地址"
}

/** 池子里现在有什么：优选结果、候选数量，或者退回官方源时的说明。 */
private fun imageIpsPoolText(): String = when {
    ImageIpsStatus.phase == ImageIpsStatus.Phase.Off -> "只用 /init 下发的地址"
    ImageIpsStatus.phase == ImageIpsStatus.Phase.Scanning && ImageIpsStatus.pool.isNotEmpty() ->
        "先用着 ${ImageIpsStatus.pool.size} 台 · ${ImageIpsStatus.fastestMs ?: 0}ms"
    ImageIpsStatus.pool.isEmpty() -> "官方源 + 内置种子"
    else -> ImageIpsStatus.fastestMs
        ?.let { "${it}ms 最快 · ${ImageIpsStatus.pool.size} 台" }
        ?: "${ImageIpsStatus.pool.size} 台"
}

/** 上一轮是什么时候测完的。 */
private fun imageIpsAgoText(seconds: Long): String = when {
    seconds < 0 -> "还没有测过"
    seconds < 60 -> "刚刚"
    seconds < 3600 -> "${seconds / 60} 分钟前"
    else -> "${seconds / 3600} 小时前"
}

/** 设置页里可点开选项的一行：图标 + 标题 + 右侧当前值 + 箭头。 */
@Composable
private fun PickerRow(
    icon: PicaIcon,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(icon, Modifier.size(18.dp), colors.onSurfaceSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.body1,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(6.dp))
        PicaGlyph(PicaIcon.ArrowRight, Modifier.size(16.dp), colors.onSurfaceSecondary)
    }
}

/** 设置页的一行开关：左边图标 + 标题 + 说明，右边 Material 3 的 Switch。 */
@Composable
private fun SwitchRow(
    icon: PicaIcon,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    /** 子开关往右缩一点，视觉上挂在上面那个开关下面。 */
    indent: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 22.dp else 0.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(
            icon = icon,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceSecondary.copy(alpha = alpha),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.onSurfaceSecondary.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(10.dp))
        PicaSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** 设置页里带地址的一行：标题 + 完整地址，整行点开浏览器。 */
@Composable
private fun LinkRow(
    icon: PicaIcon,
    title: String,
    url: String,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PicaGlyph(icon, Modifier.size(18.dp), colors.onSurfaceSecondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = url,
                style = MiuixTheme.textStyles.footnote2,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        PicaGlyph(PicaIcon.ArrowRight, Modifier.size(16.dp), colors.onSurfaceSecondary)
    }
}
