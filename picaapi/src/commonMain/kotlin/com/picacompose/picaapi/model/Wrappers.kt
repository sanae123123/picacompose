package com.picacompose.picaapi.model

import kotlinx.serialization.Serializable

@Serializable
data class CategoryListData(val categories: List<Category> = emptyList())

@Serializable
data class BannerListData(val banners: List<Banner> = emptyList())
