package com.picacompose.data

import com.hiczp.picacomic.api.PicaComicClient
import io.ktor.client.engine.cio.CIO

/** 桌面端用 CIO 引擎，和 README 的推荐一致。 */
fun createPicaComicClient(): PicaComicClient<*> = PicaComicClient(CIO)
