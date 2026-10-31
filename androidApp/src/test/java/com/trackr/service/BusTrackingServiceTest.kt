package com.trackr.service

import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.trackr.TrackrApp
import com.trackr.core.data.repository.TrackingRepository
import com.trackr.core.domain.platform.LocationSource
import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.BusStop
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.LegPhase
import com.trackr.core.model.LiveBus
import com.trackr.core.model.TrackingTarget
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.TransitLeg
import com.trackr.core.testing.FakeLocationSource
import com.trackr.data.repository.FakeTransitRepository
import com.trackr.data.repository.ITransitRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/**
 * 追踪服务（Robolectric，SDK 34）：前台服务类型、定位开关、会话结束时停服务。
 * 实时数据用 [FakeTransitRepository]，定位用 [FakeLocationSource]，会话用 App 里真实的 [TrackingRepository]。
 */
@RunWith(RobolectricTestRunner::class)
class BusTrackingServiceTest {

    private lateinit var app: TrackrApp
    private lateinit var location: FakeLocationSource
    private lateinit var transit: FakeTransitRepository
    private val trackingRepository: TrackingRepository get() = GlobalContext.get().get()

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        location = FakeLocationSource(precisePermission = true)
        transit = FakeTransitRepository()
        loadKoinModules(
            module {
                single<LocationSource> { location }
                single<ITransitRepository> { transit }
            }
        )
    }

    // ---------------------------------------------------------------- 样例数据

    private fun lineDetail(lineId: String, buses: List<LiveBus>) = BusLineDetail(
        line = BusLineDirection(
            lineId = lineId,
            name = "59路",
            lineNo = "59",
            direction = 0,
            startStop = "始发",
            endStop = "终点",
            firstTime = "06:00",
            lastTime = "22:00"
        ),
        stops = (1..10).map { BusStop(stopId = "s$it", name = "站$it", order = it, wgsLat = 25.0 + it * 0.001, wgsLng = 119.0) },
        buses = buses
    )

    private fun routeTarget() = TrackingTarget.Route(
        group = TransitGroup(
            id = "g1",
            name = "上班",
            legs = listOf(
                TransitLeg(
                    legIndex = 0,
                    fromStopName = "站3",
                    toStopName = "站7",
                    toStopId = "s7",
                    candidateLines = listOf(
                        CandidateLine(cityId = "034", lineId = "L59", lineName = "59路", fromStopOrder = 3, toStopOrder = 7)
                    )
                )
            )
        )
    )

    private fun singleTarget() = TrackingTarget.SingleLine(
        cityId = "034",
        lineId = "L59",
        lineName = "59路",
        targetStopId = "s7",
        targetStopName = "站7",
        targetOrder = 7,
        totalStops = 10
    )

    private fun startService(sessionId: String): ServiceController<BusTrackingService> =
        Robolectric.buildService(BusTrackingService::class.java, BusTrackingService.startIntent(app, sessionId))
            .create()
            .startCommand(0, 1)

    /** 主线程消息跑完，等 IO 线程的网络回调回到主线程 */
    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) fail("等待超时：$message")
            Thread.sleep(20)
        }
    }

    // ---------------------------------------------------------------- 用例

    @Test
    fun route_withPrecisePermission_usesLocationTypeAndTurnsLocationOnNearBoardStop() {
        // 车在第 2 站、离上车站 A（第 3 站）1 站：等车近站档位要开定位
        transit.realtime.lineDetailResult = Result.success(
            lineDetail("L59", listOf(LiveBus(busId = "b1", plateOrFleetNo = "闽B1", order = 2, segmentProgress = 0.5f)))
        )
        val session = requireNotNull(trackingRepository.start(routeTarget()))
        val service = startService(session.sessionId).get()

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            service.lastForegroundServiceType
        )
        assertNotNull(shadowOf(service).lastForegroundNotification)

        waitUntil("第一次轮询写回仓库") { trackingRepository.legState.value.currentBusId == "b1" }
        assertEquals(LegPhase.WAITING_BOARD, trackingRepository.legState.value.legPhase)
        assertEquals(LocationCadence.Tier.WAITING_NEAR, service.currentLocationTier)
        assertEquals(listOf(LocationCadence.Tier.WAITING_NEAR), location.requestedTiers)
        assertEquals(1, location.activeCollectors)
    }

    @Test
    fun route_withoutPrecisePermission_fallsBackToSpecialUseAndNoLocation() {
        location.precisePermission = false
        transit.realtime.lineDetailResult = Result.success(
            lineDetail("L59", listOf(LiveBus(busId = "b1", plateOrFleetNo = "闽B1", order = 2, segmentProgress = 0.5f)))
        )
        val session = requireNotNull(trackingRepository.start(routeTarget()))
        val service = startService(session.sessionId).get()

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.lastForegroundServiceType)
        waitUntil("第一次轮询写回仓库") { trackingRepository.legState.value.currentBusId == "b1" }
        assertEquals(LocationCadence.Tier.OFF, service.currentLocationTier)
        assertTrue(location.requestedTiers.isEmpty())
        assertEquals(0, location.activeCollectors)
    }

    @Test
    fun singleLine_neverAsksForLocationType() {
        transit.realtime.lineDetailResult = Result.success(
            lineDetail("L59", listOf(LiveBus(busId = "b1", plateOrFleetNo = "闽B1", order = 6, segmentProgress = 0.5f)))
        )
        val session = requireNotNull(trackingRepository.start(singleTarget()))
        val service = startService(session.sessionId).get()

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.lastForegroundServiceType)
        waitUntil("第一次轮询写回仓库") { trackingRepository.legState.value.currentBusId == "b1" }
        assertEquals(LegPhase.SINGLE, trackingRepository.legState.value.legPhase)
        assertTrue(location.requestedTiers.isEmpty())
    }

    @Test
    fun stop_turnsLocationOffAndStopsService() {
        transit.realtime.lineDetailResult = Result.success(
            lineDetail("L59", listOf(LiveBus(busId = "b1", plateOrFleetNo = "闽B1", order = 2, segmentProgress = 0.5f)))
        )
        val session = requireNotNull(trackingRepository.start(routeTarget()))
        val service = startService(session.sessionId).get()
        waitUntil("定位打开") { location.activeCollectors == 1 }

        // 通知「结束」按钮 / 首页结束追踪都是清掉仓库里的会话
        trackingRepository.stop()
        waitUntil("服务停下") { shadowOf(service).isStoppedBySelf }

        assertEquals(LocationCadence.Tier.OFF, service.currentLocationTier)
        assertEquals(0, location.activeCollectors)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertNull(trackingRepository.session.value)
    }

    @Test
    fun staleStartRequest_entersForegroundThenStops() {
        val session = requireNotNull(trackingRepository.start(singleTarget()))
        // 启动请求还没到，用户又点了结束
        trackingRepository.stop()
        val service = startService(session.sessionId).get()

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(shadowOf(service).isForegroundStopped)
    }

    @Test
    fun destroy_endsTrackedSession() {
        transit.realtime.lineDetailResult = Result.success(
            lineDetail("L59", listOf(LiveBus(busId = "b1", plateOrFleetNo = "闽B1", order = 6, segmentProgress = 0.5f)))
        )
        val session = requireNotNull(trackingRepository.start(singleTarget()))
        val controller = startService(session.sessionId)
        waitUntil("第一次轮询写回仓库") { trackingRepository.legState.value.currentBusId == "b1" }

        controller.destroy()

        assertNull(trackingRepository.session.value)
        assertFalse(trackingRepository.legState.value.isActive)
    }
}
