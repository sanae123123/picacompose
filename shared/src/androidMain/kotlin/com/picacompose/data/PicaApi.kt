package com.picacompose.data

import com.hiczp.picacomic.api.PicaComicClient
import io.ktor.client.engine.okhttp.OkHttp

/**
 * picacomic-api 是纯 JVM 库（ktor 1.x + caeruleum），只能挂在 androidMain / jvmMain。
 * iOS 端需要另写基于 Ktor 3 的客户端。
 */
fun createPicaComicClient(): PicaComicClient<*> = PicaComicClient(OkHttp) {
    // 这里可以加代理等配置
}
