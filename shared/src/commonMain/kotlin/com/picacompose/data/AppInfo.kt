package com.picacompose.data

/**
 * 应用版本号，设置页「关于」里那一行。
 *
 * 和 `androidApp/build.gradle.kts` 的 `versionName` 一起改；两边对不上时以 build.gradle 为准，
 * 这里只是界面要显示一份（shared 模块读不到 Android 的 BuildConfig）。
 */
const val APP_VERSION = "0.0.3"

/** 源码仓库，设置页给个能点开的地方。 */
const val REPO_URL = "https://github.com/sanae123123/picacompose"
