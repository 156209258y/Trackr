package com.trackr.ui.navigation

import kotlinx.serialization.Serializable

/** 小组件 / 快捷方式 / 通知带来的 6 个 extra，第 18 步换成 `trackr://` URI + `DeepLinkParser`。 */
@Serializable
data class PendingDeepLink(
    val cityId: String,
    val lineId: String,
    val targetOrder: Int = 0,
    val stopId: String = "",
    val sourceName: String = "",
    val lineName: String = ""
)
