package com.trackr.ui.screens.stopdetail

import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.NearbyStopLineStatus
import com.trackr.core.model.TransitCity
import com.trackr.core.data.repository.RealtimeRepository
import com.trackr.core.testing.FakeFavoritesRepository
import com.trackr.core.testing.FakeRealtimeRepository
import com.trackr.core.testing.FakeSettingsRepository
import com.trackr.core.testing.FakeUserLocationStore
import com.trackr.core.testing.MainDispatcherHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 记录站点请求次数、可挂起请求的仓库包装（其余行为委托给 FakeRealtimeRepository）
 */
private class CountingStopRepository(
    private val delegate: FakeRealtimeRepository
) : RealtimeRepository by delegate {
    val stopDetailPhysicalIds = mutableListOf<String?>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getStopDetail(
        city: TransitCity,
        stopId: String?,
        stopName: String?,
        physicalStId: String?,
        namesakeStId: String?,
        stationLat: Double?,
        stationLng: Double?,
        userLat: Double?,
        userLng: Double?
    ): Result<List<NearbyStop>> {
        stopDetailPhysicalIds += physicalStId
        gate?.await()
        return delegate.getStopDetail(city, stopId, stopName, physicalStId, namesakeStId, stationLat, stationLng, userLat, userLng)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StopDetailViewModelTest {

    private val main = MainDispatcherHolder()

    private lateinit var settings: FakeSettingsRepository
    private lateinit var favorites: FakeFavoritesRepository
    private lateinit var fakeRepository: FakeRealtimeRepository
    private val userLocationStore = FakeUserLocationStore()
    private val defaultCity = TransitCity(cityId = "034", cityName = "上海")

    private val sampleStop1 = NearbyStop(
        stopId = "stop_001",
        name = "人民广场（广东路）",
        physicalStId = "phy_01",
        lines = listOf(
            NearbyStopLineStatus(
                line = BusLineDirection(
                    lineId = "line_49",
                    name = "49路",
                    startStop = "汉口路",
                    endStop = "上海体育馆",
                    source = DataSourceType.CHELAILE
                ),
                targetOrder = 2,
                targetStopId = "stop_001",
                targetStopName = "人民广场（广东路）",
                statusText = "即将到站"
            )
        )
    )

    private val sampleStop2 = NearbyStop(
        stopId = "stop_002",
        name = "人民广场（福州路）",
        physicalStId = "phy_02",
        lines = listOf(
            NearbyStopLineStatus(
                line = BusLineDirection(
                    lineId = "line_18",
                    name = "18路",
                    startStop = "鲁迅公园",
                    endStop = "世博会博物馆",
                    source = DataSourceType.CHELAILE
                ),
                targetOrder = 5,
                targetStopId = "stop_002",
                targetStopName = "人民广场（福州路）",
                statusText = "2站 / 5分钟"
            )
        )
    )

    @BeforeTest
    fun setup() {
        main.install()
        settings = FakeSettingsRepository(defaultCity)
        favorites = FakeFavoritesRepository()
        fakeRepository = FakeRealtimeRepository(currentCity = { settings.selectedCity.value })
        fakeRepository.stopDetailResult = Result.success(listOf(sampleStop1, sampleStop2))
    }

    @AfterTest
    fun tearDown() {
        main.reset()
    }

    private fun newViewModel(
        realtime: RealtimeRepository = fakeRepository,
        enableAutoPolling: Boolean,
        clockMillis: (() -> Long)? = null
    ): StopDetailViewModel = if (clockMillis == null) {
        StopDetailViewModel(
            cityId = "034", stopId = "stop_001", initialStopName = "人民广场",
            settings = settings, favorites = favorites, realtime = realtime, userLocationStore = userLocationStore,
            enableAutoPolling = enableAutoPolling
        )
    } else {
        StopDetailViewModel(
            cityId = "034", stopId = "stop_001", initialStopName = "人民广场",
            settings = settings, favorites = favorites, realtime = realtime, userLocationStore = userLocationStore,
            enableAutoPolling = enableAutoPolling, clockMillis = clockMillis
        )
    }

    private fun createViewModel(): StopDetailViewModel = newViewModel(enableAutoPolling = false)

    @Test
    fun testInitialLoad_populatesStopsAndResolvesName() = runTest(main.dispatcher) {
        val viewModel = createViewModel()

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.stops.size)
        assertEquals("人民广场（广东路）", state.stopName)
        assertEquals(0, state.selectedStopIndex)
        assertNull(state.errorMessage)
    }

    @Test
    fun testSelectStopIndex_updatesSelectedStop() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.selectStopIndex(1)
        assertEquals(1, viewModel.uiState.value.selectedStopIndex)
    }

    @Test
    fun testToggleFavorite_updatesFavoriteIds() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        val targetLine = sampleStop1.lines.first()
        viewModel.toggleFavorite(sampleStop1, targetLine)
        testScheduler.runCurrent()

        val expectedFavId = "034_line_49_2"
        assertTrue(viewModel.uiState.value.favoriteIds.contains(expectedFavId))

        // Toggle again to remove
        viewModel.toggleFavorite(sampleStop1, targetLine)
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.favoriteIds.contains(expectedFavId))
    }

    @Test
    fun testPollingLifecycle_pauseResumeAndExplicitControl() = runTest(main.dispatcher) {
        val vmWithAutoPolling = newViewModel(enableAutoPolling = true)
        assertTrue(vmWithAutoPolling.isPollingActive)

        vmWithAutoPolling.pausePolling()
        assertFalse(vmWithAutoPolling.isPollingActive)

        vmWithAutoPolling.resumePolling()
        assertTrue(vmWithAutoPolling.isPollingActive)

        vmWithAutoPolling.setPollingActive(false)
        assertFalse(vmWithAutoPolling.isPollingActive)

        vmWithAutoPolling.setPollingActive(true)
        assertTrue(vmWithAutoPolling.isPollingActive)

        vmWithAutoPolling.pausePolling()
    }

    @Test
    fun testPollingLifecycle_disabledByDefaultInTest() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        assertFalse(viewModel.isPollingActive)

        viewModel.setPollingActive(true)
        assertTrue(viewModel.isPollingActive)

        viewModel.pausePolling()
        assertFalse(viewModel.isPollingActive)
    }

    @Test
    fun testSelectStopIndex_changedPlatform_refreshesWithThatPlatform() = runTest(main.dispatcher) {
        val repo = CountingStopRepository(fakeRepository)
        val viewModel = newViewModel(realtime = repo, enableAutoPolling = false)
        testScheduler.runCurrent()
        assertEquals(1, repo.stopDetailPhysicalIds.size)

        viewModel.selectStopIndex(1)
        testScheduler.runCurrent()
        assertEquals(listOf<String?>(null, "phy_02"), repo.stopDetailPhysicalIds)
        assertEquals(1, viewModel.uiState.value.selectedStopIndex)

        // 选中同一站台不重复请求
        viewModel.selectStopIndex(1)
        testScheduler.runCurrent()
        assertEquals(2, repo.stopDetailPhysicalIds.size)
    }

    @Test
    fun testPolling_skipsWhenPreviousLoadStillInFlight() = runTest(main.dispatcher) {
        val repo = CountingStopRepository(fakeRepository)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        val viewModel = newViewModel(realtime = repo, enableAutoPolling = true)
        testScheduler.runCurrent()
        assertTrue(viewModel.isLoadInFlight)

        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertEquals(1, repo.stopDetailPhysicalIds.size)

        gate.complete(Unit)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(10_000L)
        testScheduler.runCurrent()
        assertEquals(2, repo.stopDetailPhysicalIds.size)

        viewModel.pausePolling()
    }

    @Test
    fun testScreenResume_refreshesOnlyWhenStale() = runTest(main.dispatcher) {
        var now = 0L
        val repo = CountingStopRepository(fakeRepository)
        val viewModel = newViewModel(realtime = repo, enableAutoPolling = true, clockMillis = { now })
        testScheduler.runCurrent()

        viewModel.onScreenResumed()
        testScheduler.runCurrent()
        assertEquals(1, repo.stopDetailPhysicalIds.size)

        viewModel.onScreenPaused()
        assertFalse(viewModel.isPollingActive)

        now += 11_000L
        viewModel.onScreenResumed()
        testScheduler.runCurrent()
        assertEquals(2, repo.stopDetailPhysicalIds.size)
        assertTrue(viewModel.isPollingActive)

        viewModel.onScreenPaused()
    }

    @Test
    fun testScreenResume_noopWhenAutoPollingDisabled() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.onScreenResumed()
        assertFalse(viewModel.isPollingActive)
    }
}
