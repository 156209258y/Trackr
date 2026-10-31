package com.trackr.core.model

import kotlinx.serialization.Serializable

/*
 * 收藏合一（重构计划第 14 步）：收藏的车（站 + 线）和路线排在同一个列表里，共用一套顺序和分组。
 */

/** 收藏的类型：车（站 + 线）/ 路线 */
@Serializable
enum class FavoriteItemType { STOP, ROUTE }

/**
 * 一条收藏在合一列表里的位置和分组（存储层的一行，`favorite_item` 表 / 旧存储的 `favorite_items_json`）。
 *
 * @param refId 车是 [FavoriteStationLine.id]，路线是 [TransitGroup.id]
 * @param groupId 收藏分组 [FavoriteGroup.id]；null = 不分组
 * @param sortOrder 合一列表里的顺序，小的在前；不要求连续
 */
@Serializable
data class FavoriteItemRecord(
    val type: FavoriteItemType,
    val refId: String,
    val groupId: String? = null,
    val sortOrder: Int = 0
) {
    val key: String get() = FavoriteItem.keyOf(type, refId)
}

/**
 * 合一列表里的一条收藏。[key] 在两种类型之间也不重复，可以直接当 Lazy 列表的 key（§4.3）。
 */
sealed interface FavoriteItem {
    val key: String
    val groupId: String?
    val sortOrder: Int

    data class Stop(
        val stop: FavoriteStationLine,
        override val groupId: String?,
        override val sortOrder: Int
    ) : FavoriteItem {
        override val key: String get() = stopKey(stop.id)
    }

    data class Route(
        val route: TransitGroup,
        override val groupId: String?,
        override val sortOrder: Int
    ) : FavoriteItem {
        override val key: String get() = routeKey(route.id)
    }

    companion object {
        const val STOP_KEY_PREFIX = "stop_"
        const val ROUTE_KEY_PREFIX = "route_"

        fun stopKey(stopId: String): String = STOP_KEY_PREFIX + stopId
        fun routeKey(routeId: String): String = ROUTE_KEY_PREFIX + routeId

        fun keyOf(type: FavoriteItemType, refId: String): String = when (type) {
            FavoriteItemType.STOP -> stopKey(refId)
            FavoriteItemType.ROUTE -> routeKey(refId)
        }
    }
}
