package com.picacompose.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

@Composable
actual fun hotTileArt(name: String): Painter? {
    val context = LocalContext.current
    val id = remember(name, context) {
        context.resources.getIdentifier("hot_cat_$name", "drawable", context.packageName)
    }
    return if (id != 0) painterResource(id) else null
}
