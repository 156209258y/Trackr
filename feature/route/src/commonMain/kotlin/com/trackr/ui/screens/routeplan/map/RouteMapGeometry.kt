package com.trackr.ui.screens.routeplan.map

import com.trackr.core.domain.math.CoordTransform
import com.trackr.core.model.AmapTransitSchemeResult
import com.trackr.core.model.GeoPoint
import com.trackr.core.model.ProvinceCityCatalog

// 地图要画什么（纯函数，第 24 步从旧 RoutePlanScreen 的 AmapRealVectorMapView 里拆出来，平台实现只管画）。
// 所有点都是 GCJ-02（高德方案自带的坐标就是 GCJ-02）。

/** 选中方案的一个覆盖物；平台实现按列表顺序画（先画的在下面） */
sealed interface RouteMapOverlay {
    /** 步行：虚线 */
    data class Walk(val points: List<GeoPoint>) : RouteMapOverlay

    /** 乘车：第 [legIndex] 程的实线（第 1 程主色，之后换色） */
    data class Ride(val points: List<GeoPoint>, val legIndex: Int) : RouteMapOverlay

    /** 点标记：换乘站 / 起点 / 终点 */
    data class Marker(val point: GeoPoint, val title: String) : RouteMapOverlay
}

/** 未选中方案的一条细线：点序列 + 是否步行 */
data class SchemeLine(val points: List<GeoPoint>, val isWalk: Boolean)

private fun stopPoint(lat: Double, lng: Double): GeoPoint? =
    if (lat > 0.0 && lng > 0.0) GeoPoint(lat, lng) else null

/** 方案起点：优先用户选择的出发地 POI，否则回落首段上车站 */
fun schemeOrigin(scheme: AmapTransitSchemeResult): GeoPoint? =
    scheme.originPoint
        ?: scheme.group.legs.firstOrNull()?.let { stopPoint(it.fromStopLat, it.fromStopLng) }

/** 方案终点：优先用户选择的目的地 POI，否则回落末段下车站 */
fun schemeDestination(scheme: AmapTransitSchemeResult): GeoPoint? =
    scheme.destinationPoint
        ?: scheme.group.legs.lastOrNull()?.let { stopPoint(it.toStopLat, it.toStopLng) }

/**
 * 方案的折线（画未选中方案的细灰线）：乘车优先用 ridePath 形状点，没有才用上下车站直线；
 * 步行用 walkPathBefore / finalWalkPath。
 */
fun schemeLines(scheme: AmapTransitSchemeResult): List<SchemeLine> {
    val result = mutableListOf<SchemeLine>()
    scheme.group.legs.forEach { leg ->
        if (leg.walkPathBefore.size >= 2) result += SchemeLine(leg.walkPathBefore, isWalk = true)
        val from = stopPoint(leg.fromStopLat, leg.fromStopLng)
        val to = stopPoint(leg.toStopLat, leg.toStopLng)
        if (leg.ridePath.size >= 2) {
            result += SchemeLine(leg.ridePath, isWalk = false)
        } else if (from != null && to != null) {
            result += SchemeLine(listOf(from, to), isWalk = false)
        }
    }
    if (scheme.finalWalkPath.size >= 2) result += SchemeLine(scheme.finalWalkPath, isWalk = true)
    return result
}

/**
 * 选中方案的覆盖物（743e932：按 polyline 形状点画，不是两点直线）：
 * - 上车前步行：优先真实 walkPathBefore，否则换乘两站直连虚线；
 * - 乘车：优先真实 ridePath，为空时回落上下车站两点；
 * - 第 2 程起在上车站放「换乘」标记；
 * - 末段步行：优先 finalWalkPath，否则末站连到目的地 POI；
 * - 最后放起点、终点标记。
 */
fun selectedSchemeOverlays(scheme: AmapTransitSchemeResult): List<RouteMapOverlay> = buildList {
    fun walk(points: List<GeoPoint>) {
        if (points.size >= 2) add(RouteMapOverlay.Walk(points))
    }

    var prevLegTo: GeoPoint? = null
    scheme.group.legs.forEachIndexed { index, leg ->
        val from = stopPoint(leg.fromStopLat, leg.fromStopLng)
        val to = stopPoint(leg.toStopLat, leg.toStopLng)

        val previous = prevLegTo
        if (leg.walkPathBefore.size >= 2) {
            walk(leg.walkPathBefore)
        } else if (previous != null && from != null && previous != from) {
            walk(listOf(previous, from))
        }

        if (leg.ridePath.size >= 2) {
            add(RouteMapOverlay.Ride(leg.ridePath, index))
        } else if (from != null && to != null) {
            add(RouteMapOverlay.Ride(listOf(from, to), index))
        }

        if (from != null && index != 0) {
            add(RouteMapOverlay.Marker(from, "换乘: ${leg.fromStopName}"))
        }
        if (to != null) prevLegTo = to
    }

    val lastTo = prevLegTo
    val destinationPoi = scheme.destinationPoint
    if (scheme.finalWalkPath.size >= 2) {
        walk(scheme.finalWalkPath)
    } else if (lastTo != null && destinationPoi != null && lastTo != destinationPoi) {
        walk(listOf(lastTo, destinationPoi))
    }

    schemeOrigin(scheme)?.let { add(RouteMapOverlay.Marker(it, "起点")) }
    schemeDestination(scheme)?.let { add(RouteMapOverlay.Marker(it, "终点")) }
}

/** 方案里所有用于取景的点（折线 + 站点 + 起终点） */
fun schemeBoundsPoints(scheme: AmapTransitSchemeResult): List<GeoPoint> = buildList {
    schemeLines(scheme).forEach { addAll(it.points) }
    scheme.group.legs.forEach { leg ->
        stopPoint(leg.fromStopLat, leg.fromStopLng)?.let { add(it) }
        stopPoint(leg.toStopLat, leg.toStopLng)?.let { add(it) }
    }
    schemeOrigin(scheme)?.let { add(it) }
    schemeDestination(scheme)?.let { add(it) }
}

/** 系统定位是 WGS-84，高德地图要 GCJ-02；两者在国内差 300~600 米 */
fun wgs84ToGcj02Point(lat: Double, lng: Double): GeoPoint {
    val (gcjLat, gcjLng) = CoordTransform.wgs84ToGcj02(lat, lng)
    return GeoPoint(gcjLat, gcjLng)
}

/**
 * 定位不可用时的兜底取景：当前城市中心。城市中心表是 WGS-84，先转 GCJ-02 再给地图（301c124，否则偏 300~600 米）。
 * 没收录的城市返回 null（宁可不动相机，也不回落到某个内置城市）。
 */
fun cityCenterGcj02(cityName: String): GeoPoint? =
    ProvinceCityCatalog.findCityCenterCoordinates(cityName)
        ?.let { (lat, lng) -> wgs84ToGcj02Point(lat, lng) }
