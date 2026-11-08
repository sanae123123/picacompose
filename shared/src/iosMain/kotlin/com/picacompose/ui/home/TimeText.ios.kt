package com.picacompose.ui.home

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

internal actual fun localUtcOffsetSeconds(): Long =
    NSTimeZone.localTimeZone.secondsFromGMT().toLong()
