package com.picacompose.data

import com.picacompose.picaapi.PicaClient

/**
 * App 里默认共用的客户端。登录后 token 会存在客户端内部，直接复用即可。
 * 需要多账号 / 多配置时自己 new PicaClient(config)。
 */
val pica: PicaClient by lazy { PicaClient() }
