package com.trackr.core.network.remote.mygolbs

import com.trackr.core.model.TransitCity

/**
 * 掌上公交 `mg:` 协议 id 的唯一编解码入口。
 *
 * - 线路：`mg:{cityCode}:{cityName}:{lineName}:{direction}`
 * - 站点：`mg:{cityName}:{stationName}`
 *
 * 以前这些前缀、拆分和 `removeSuffix` 散在 API 客户端、Provider、协调器里，
 * 六七个地方各写一遍；新增字段或修格式时容易漏掉一处。统一放这里。
 */
object MygolbsIds {
    const val PREFIX = "mg:"

    /** 是否是掌上公交协议的 id（线路、站点共用同一个前缀） */
    fun isMygolbsId(id: String?): Boolean = id?.startsWith(PREFIX) == true

    /** 解析后的线路坐标 */
    data class LineRef(
        val cityCode: String,
        val cityName: String,
        val lineName: String,
        val direction: String
    ) {
        /** 1 / 2（掌上公交方向），非法值按 1 */
        val directionCode: Int
            get() = direction.toIntOrNull() ?: 1

        val isReverse: Boolean
            get() = directionCode == 2

        /** 反向方向：1 <-> 2 */
        fun reversed(): LineRef = copy(direction = if (isReverse) "1" else "2")

        fun lineId(): String = lineId(cityCode, cityName, lineName, direction)
    }

    fun lineId(cityCode: String, cityName: String, lineName: String, direction: String): String =
        "$PREFIX$cityCode:$cityName:$lineName:$direction"

    /**
     * 解析 `mg:` 线路 id。线路名本身可能带 `:`，所以只把结尾一段当方向，
     * 中间的部分全部还给线路名。格式不对返回 null。
     */
    fun parseLineId(lineId: String): LineRef? {
        if (!isMygolbsId(lineId)) return null
        val parts = lineId.removePrefix(PREFIX).split(":")
        if (parts.size < 4) return null
        val direction = parts.last()
        if (direction.toIntOrNull() == null) return null
        return LineRef(
            cityCode = parts[0],
            cityName = parts[1],
            lineName = parts.subList(2, parts.size - 1).joinToString(":"),
            direction = direction
        )
    }

    /**
     * 解析线路 id，并用当前城市补齐缺失/占位的城市码与城市名
     * （附近站拿到的 id 里城市码是 `0`，用真实城市码查询更稳）。
     */
    fun lineRef(lineId: String, city: TransitCity? = null): LineRef? {
        val parsed = parseLineId(lineId) ?: return null
        return parsed.copy(
            cityCode = city?.mygolbsCityCode?.takeIf { it.isNotBlank() } ?: parsed.cityCode,
            cityName = city?.cityName?.takeIf { it.isNotBlank() } ?: parsed.cityName
        )
    }

    fun stopId(cityName: String, stationName: String): String = "$PREFIX$cityName:$stationName"

    /** 从任意 `mg:` 站点 id 里取站名（兼容 `mg:城市:站名` 与 `mg:城市码:城市:站名`） */
    fun stopNameOf(id: String?): String? = id
        ?.takeIf(::isMygolbsId)
        ?.removePrefix(PREFIX)
        ?.split(":")
        ?.lastOrNull()
        ?.takeIf { it.isNotBlank() }

    /** 去掉各页面为了区分数据源追加的中文后缀 */
    fun cleanStationName(raw: String): String = raw
        .removeSuffix(" (掌上公交)")
        .removeSuffix("(掌上公交)")
        .removeSuffix("（掌上公交）")
        .removeSuffix(" [掌上公交]")
        .removeSuffix("[掌上公交]")
        .trim()
}
