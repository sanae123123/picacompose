package com.picacompose.picaapi

import platform.Foundation.NSDate

internal actual fun nowEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
