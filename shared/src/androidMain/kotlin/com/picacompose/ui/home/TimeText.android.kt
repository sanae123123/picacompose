package com.picacompose.ui.home

import java.util.TimeZone

internal actual fun localUtcOffsetSeconds(): Long =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000L
