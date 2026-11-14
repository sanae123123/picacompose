package com.picacompose.picaapi

import platform.Foundation.NSDate

actual fun nowEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
