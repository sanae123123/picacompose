package com.picacompose.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.ArrowForward as RoundedArrowForward
import androidx.compose.material.icons.rounded.Category as RoundedCategory
import androidx.compose.material.icons.rounded.Check as RoundedCheck
import androidx.compose.material.icons.rounded.Close as RoundedClose
import androidx.compose.material.icons.rounded.Edit as RoundedEdit
import androidx.compose.material.icons.rounded.Email as RoundedEmail
import androidx.compose.material.icons.rounded.FilterList as RoundedFilterList
import androidx.compose.material.icons.rounded.Menu as RoundedMenu
import androidx.compose.material.icons.rounded.Sort as RoundedSort
import androidx.compose.material.icons.rounded.EventAvailable as RoundedEventAvailable
import androidx.compose.material.icons.rounded.Home as RoundedHome
import androidx.compose.material.icons.rounded.Lock as RoundedLock
import androidx.compose.material.icons.rounded.Logout as RoundedLogout
import androidx.compose.material.icons.rounded.Notifications as RoundedNotifications
import androidx.compose.material.icons.rounded.Person as RoundedPerson
import androidx.compose.material.icons.rounded.Search as RoundedSearch
import androidx.compose.material.icons.rounded.Settings as RoundedSettings
import androidx.compose.material.icons.rounded.Bolt as RoundedBolt
import androidx.compose.material.icons.rounded.Shield as RoundedShield
import androidx.compose.material.icons.rounded.Speed as RoundedSpeed
import androidx.compose.material.icons.rounded.SportsEsports as RoundedSportsEsports
import androidx.compose.material.icons.rounded.Star as RoundedStar
import androidx.compose.material.icons.rounded.ThumbUp as RoundedThumbUp
import androidx.compose.material.icons.rounded.Visibility as RoundedVisibility
import androidx.compose.material.icons.rounded.VisibilityOff as RoundedVisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 界面用到的图标，全部来自 Google 官方 Material Icons。
 *
 * 默认用 outlined，底部导航选中时换 filled —— Material You 里就是这么区分的。
 */
enum class PicaIcon {
    Mail, Back, Lock, Person, Eye, EyeOff, ArrowRight, Logout, Home, Grid, Gamepad, Gear, CheckIn, Notice, Like, Edit,
    Menu, Check, Sort, Star, Close, Search, Filter, Speed, Bolt, Shield,
}

private fun PicaIcon.vector(filled: Boolean): ImageVector = when (this) {
    PicaIcon.Mail -> if (filled) Icons.Rounded.RoundedEmail else Icons.Outlined.Email
    PicaIcon.Edit -> if (filled) Icons.Rounded.RoundedEdit else Icons.Outlined.Edit
    PicaIcon.Back -> if (filled) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Outlined.ArrowBack
    PicaIcon.Lock -> if (filled) Icons.Rounded.RoundedLock else Icons.Outlined.Lock
    PicaIcon.Person -> if (filled) Icons.Rounded.RoundedPerson else Icons.Outlined.Person
    PicaIcon.Eye -> if (filled) Icons.Rounded.RoundedVisibility else Icons.Outlined.Visibility
    PicaIcon.EyeOff -> if (filled) Icons.Rounded.RoundedVisibilityOff else Icons.Outlined.VisibilityOff
    PicaIcon.ArrowRight -> if (filled) Icons.Rounded.RoundedArrowForward else Icons.Outlined.ArrowForward
    PicaIcon.Logout -> if (filled) Icons.Rounded.RoundedLogout else Icons.Outlined.Logout
    PicaIcon.Home -> if (filled) Icons.Rounded.RoundedHome else Icons.Outlined.Home
    PicaIcon.Grid -> if (filled) Icons.Rounded.RoundedCategory else Icons.Outlined.Category
    PicaIcon.Gamepad -> if (filled) Icons.Rounded.RoundedSportsEsports else Icons.Outlined.SportsEsports
    PicaIcon.Gear -> if (filled) Icons.Rounded.RoundedSettings else Icons.Outlined.Settings
    PicaIcon.CheckIn -> if (filled) Icons.Rounded.RoundedEventAvailable else Icons.Outlined.EventAvailable
    PicaIcon.Notice -> if (filled) Icons.Rounded.RoundedNotifications else Icons.Outlined.Notifications
    PicaIcon.Like -> if (filled) Icons.Rounded.RoundedThumbUp else Icons.Outlined.ThumbUp
    PicaIcon.Menu -> if (filled) Icons.Rounded.RoundedMenu else Icons.Outlined.Menu
    PicaIcon.Check -> if (filled) Icons.Rounded.RoundedCheck else Icons.Outlined.Check
    PicaIcon.Sort -> if (filled) Icons.Rounded.RoundedSort else Icons.Outlined.Sort
    PicaIcon.Star -> if (filled) Icons.Rounded.RoundedStar else Icons.Outlined.Star
    PicaIcon.Close -> if (filled) Icons.Rounded.RoundedClose else Icons.Outlined.Close
    PicaIcon.Search -> if (filled) Icons.Rounded.RoundedSearch else Icons.Outlined.Search
    PicaIcon.Filter -> if (filled) Icons.Rounded.RoundedFilterList else Icons.Outlined.FilterList
    PicaIcon.Speed -> if (filled) Icons.Rounded.RoundedSpeed else Icons.Outlined.Speed
    PicaIcon.Bolt -> if (filled) Icons.Rounded.RoundedBolt else Icons.Outlined.Bolt
    PicaIcon.Shield -> if (filled) Icons.Rounded.RoundedShield else Icons.Outlined.Shield
}

@Composable
fun PicaGlyph(
    icon: PicaIcon,
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
) {
    Icon(
        imageVector = icon.vector(filled),
        contentDescription = null,
        tint = tint,
        modifier = modifier,
    )
}
