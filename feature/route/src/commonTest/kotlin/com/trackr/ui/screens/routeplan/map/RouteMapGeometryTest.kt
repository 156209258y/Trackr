package com.trackr.ui.screens.routeplan.map

import com.trackr.core.model.AmapTransitSchemeResult
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.GeoPoint
import com.trackr.core.model.ProvinceCityCatalog
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.TransitLeg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 地图画什么：按形状点画线（743e932）、缺点才退回两点、城市中心转 GCJ-02（301c124） */
class RouteMapGeometryTest {

    private val p1 = GeoPoint(30.10, 120.10)
    private val p2 = GeoPoint(30.20, 120.20)
    private val p3 = GeoPoint(30.30, 120.30)

    private fun leg(
        index: Int,
        from: GeoPoint,
        to: GeoPoint,
        ridePath: List<GeoPoint> = emptyList(),
        walkBefore: List<GeoPoint> = emptyList()
    ) = TransitLeg(
        legIndex = index,
        fromStopName = "上$index",
        toStopName = "下$index",
        fromStopLat = from.lat,
        fromStopLng = from.lng,
        toStopLat = to.lat,
        toStopLng = to.lng,
        candidateLines = listOf(CandidateLine(cityId = "033", lineId = "l$index", lineName = "${index}路")),
        ridePath = ridePath,
        walkPathBefore = walkBefore
    )

    private fun scheme(legs: List<TransitLeg>, destination: GeoPoint? = null, finalWalk: List<GeoPoint> = emptyList()) =
        AmapTransitSchemeResult(
            title = "s",
            durationMinutes = 30,
            walkingMeters = 300,
            costYuan = "2",
            transferCount = legs.size - 1,
            group = TransitGroup(id = "g", name = "g", legs = legs),
            destinationPoint = destination,
            finalWalkPath = finalWalk
        )

    @Test
    fun selectedScheme_ridesFollowShapePoints_notStraightLine() {
        val shape = listOf(p1, GeoPoint(30.12, 120.18), GeoPoint(30.18, 120.15), p2)
        val overlays = selectedSchemeOverlays(scheme(listOf(leg(0, p1, p2, ridePath = shape))))

        val ride = overlays.filterIsInstance<RouteMapOverlay.Ride>().single()
        assertEquals(shape, ride.points)
        assertEquals(0, ride.legIndex)
        assertEquals(listOf("起点", "终点"), overlays.filterIsInstance<RouteMapOverlay.Marker>().map { it.title })
    }

    @Test
    fun selectedScheme_withoutShape_fallsBackToStops_andWalksBetweenTransfers() {
        val transferFrom = GeoPoint(30.21, 120.21)
        val dest = GeoPoint(30.40, 120.40)
        val overlays = selectedSchemeOverlays(
            scheme(
                listOf(leg(0, p1, p2), leg(1, transferFrom, p3)),
                destination = dest
            )
        )

        assertEquals(
            listOf(
                RouteMapOverlay.Ride(listOf(p1, p2), 0),
                RouteMapOverlay.Walk(listOf(p2, transferFrom)),
                RouteMapOverlay.Ride(listOf(transferFrom, p3), 1),
                RouteMapOverlay.Marker(transferFrom, "换乘: 上1"),
                RouteMapOverlay.Walk(listOf(p3, dest)),
                RouteMapOverlay.Marker(p1, "起点"),
                RouteMapOverlay.Marker(dest, "终点")
            ),
            overlays
        )
    }

    @Test
    fun selectedScheme_prefersRealWalkPaths() {
        val walkBefore = listOf(GeoPoint(30.05, 120.05), p1)
        val finalWalk = listOf(p2, GeoPoint(30.25, 120.25))
        val overlays = selectedSchemeOverlays(
            scheme(listOf(leg(0, p1, p2, walkBefore = walkBefore)), destination = GeoPoint(30.25, 120.25), finalWalk = finalWalk)
        )
        val walks = overlays.filterIsInstance<RouteMapOverlay.Walk>().map { it.points }
        assertEquals(listOf(walkBefore, finalWalk), walks)
    }

    @Test
    fun otherSchemeLines_andBounds_useShapePoints() {
        val shape = listOf(p1, GeoPoint(30.15, 120.12), p2)
        val s = scheme(listOf(leg(0, p1, p2, ridePath = shape)))
        assertEquals(listOf(SchemeLine(shape, isWalk = false)), schemeLines(s))
        assertEquals(shape + listOf(p1, p2, p1, p2), schemeBoundsPoints(s))
    }

    @Test
    fun missingStopCoordinates_drawNothingForThatLeg() {
        val s = scheme(listOf(leg(0, GeoPoint(0.0, 0.0), p2)))
        assertEquals(emptyList(), schemeLines(s))
        assertNull(schemeOrigin(s))
    }

    @Test
    fun cityCenter_isConvertedFromWgs84ToGcj02() {
        val (wgsLat, wgsLng) = assertNotNull(ProvinceCityCatalog.findCityCenterCoordinates("杭州"))
        val gcj = assertNotNull(cityCenterGcj02("杭州"))
        assertNotEquals(GeoPoint(wgsLat, wgsLng), gcj)
        assertEquals(wgs84ToGcj02Point(wgsLat, wgsLng), gcj)
        assertNull(cityCenterGcj02("不存在的城市"))
    }
}
