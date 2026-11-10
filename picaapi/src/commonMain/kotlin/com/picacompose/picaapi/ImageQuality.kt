package com.picacompose.picaapi

/**
 * 画质档位，对应官方设置里的「原圖 / 低 / 中 / 高」。
 *
 * 取值来自官方 APK 的 `setting_options_image_qualities_server` 字符串数组，
 * 客户端把它原样塞进 `image-quality` 请求头，图床按这个头决定返回的图片尺寸。
 */
enum class ImageQuality(val header: String) {
    Original("original"),
    Low("low"),
    Medium("medium"),
    High("high"),
    ;

    companion object {
        /** 存的是索引（和官方的偏好键一样），越界或老数据一律当中等画质。 */
        fun fromIndex(index: Int): ImageQuality = entries.getOrElse(index) { Medium }
    }
}

/**
 * 当前画质的全局副本。
 *
 * 接口那条链路的头在 [PicaConfig.imageQuality] 里，换线路重建客户端时会带过去；
 * 图片走的是 Coil 自己那套引擎，[imageEngine] 里没有配置入口，只好在读拦截器里现取这个值。
 */
object ImageQualitySetting {
    @Volatile
    var current: ImageQuality = ImageQuality.Medium
}
