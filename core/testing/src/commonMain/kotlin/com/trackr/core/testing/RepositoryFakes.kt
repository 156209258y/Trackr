package com.trackr.core.testing

import com.trackr.core.data.repository.FavoritesRepository
import com.trackr.core.data.repository.LocationRepository
import com.trackr.core.data.repository.RealtimeRepository
import com.trackr.core.data.repository.SearchHistoryRepository
import com.trackr.core.data.repository.SettingsRepository
import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.AggregatedSourcePriority
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.EngineMode
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.LineTimetable
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.model.TransitSearchResult
import com.trackr.core.model.UserFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update

/*
 * :core:data 仓库接口的 Fake（原 :androidApp 的 FakeTransitRepository 按接口拆开，重构计划第 8 步）。
 * 都是内存实现，结果由测试设定；只在单线程测试（runTest）里用。
 */

/** 默认城市：上海（和原 FakeTransitRepository 一致） */
val FAKE_DEFAULT_CITY = TransitCity(cityId = "034", cityName = "上海", pinyin = "shanghai", isHot = true)

class FakeSettingsRepository(
    initialCity: TransitCity = FAKE_DEFAULT_CITY,
    initialTransitCodeProvider: TransitCodeProvider = TransitCodeProvider.ASK_ALWAYS
) : SettingsRepository {

    private val _selectedCity = MutableStateFlow(initialCity)
    override val selectedCity: StateFlow<TransitCity> = _selectedCity.asStateFlow()

    private val _hasSavedCity = MutableStateFlow(true)
    override val hasSavedCity: StateFlow<Boolean> = _hasSavedCity.asStateFlow()

    /** 测试里设为 false 模拟首次安装（从未存过城市） */
    fun setHasSavedCity(saved: Boolean) {
        _hasSavedCity.value = saved
    }

    private val _engineMode = MutableStateFlow(EngineMode.CHELAILE)
    override val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _aggregatedPriority = MutableStateFlow(AggregatedSourcePriority.CHELAILE_FIRST)
    override val aggregatedPriority: StateFlow<AggregatedSourcePriority> = _aggregatedPriority.asStateFlow()

    private val _preferredTransitCodeProvider = MutableStateFlow(initialTransitCodeProvider)
    override val preferredTransitCodeProvider: StateFlow<TransitCodeProvider> = _preferredTransitCodeProvider.asStateFlow()

    private val _nearbyStopsCount = MutableStateFlow(4)
    override val nearbyStopsCount: StateFlow<Int> = _nearbyStopsCount.asStateFlow()

    private val _isLiveNotificationEnabled = MutableStateFlow(true)
    override val isLiveNotificationEnabled: StateFlow<Boolean> = _isLiveNotificationEnabled.asStateFlow()

    override fun setEngineMode(mode: EngineMode) {
        _engineMode.value = mode
    }

    override fun setAggregatedPriority(priority: AggregatedSourcePriority) {
        _aggregatedPriority.value = priority
    }

    override fun setNearbyStopsCount(count: Int) {
        _nearbyStopsCount.value = count.coerceIn(1, 16)
    }

    override fun setLiveNotificationEnabled(enabled: Boolean) {
        _isLiveNotificationEnabled.value = enabled
    }

    override suspend fun selectCity(city: TransitCity) {
        _selectedCity.value = city
        _hasSavedCity.value = true
    }

    override suspend fun setPreferredTransitCodeProvider(provider: TransitCodeProvider) {
        _preferredTransitCodeProvider.value = provider
    }
}

/** 收藏 Fake：新收藏加在末尾（和原 FakeTransitRepository 一致） */
class FakeFavoritesRepository(
    initialFavorites: List<FavoriteStationLine> = emptyList()
) : FavoritesRepository {

    private val _favorites = MutableStateFlow(initialFavorites)
    override val favorites: StateFlow<List<FavoriteStationLine>> = _favorites.asStateFlow()

    override suspend fun toggleFavorite(item: FavoriteStationLine): Boolean {
        val exists = _favorites.value.any { it.id == item.id }
        return if (exists) {
            _favorites.update { list -> list.filterNot { it.id == item.id } }
            false
        } else {
            _favorites.update { list -> list + item }
            true
        }
    }

    override suspend fun removeFavorite(id: String) {
        _favorites.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeSearchHistoryRepository(
    initialSearchHistory: List<String> = emptyList()
) : SearchHistoryRepository {

    private val _searchHistory = MutableStateFlow(initialSearchHistory)
    override val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    override suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchHistory.update { list ->
            val updated = list.filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableList()
            updated.add(0, trimmed)
            if (updated.size > 20) updated.take(20) else updated
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        val trimmed = query.trim()
        _searchHistory.update { list -> list.filterNot { it.equals(trimmed, ignoreCase = true) } }
    }

    override suspend fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }
}

/**
 * 实时查询 Fake：每个查询返回测试设定的结果。
 * @param currentCity 定位城市默认返回的「当前城市」（原 Fake 返回已选城市）
 */
class FakeRealtimeRepository(
    private val currentCity: () -> TransitCity = { FAKE_DEFAULT_CITY }
) : RealtimeRepository {

    var supportedCitiesResult: Result<List<TransitCity>> = Result.success(listOf(currentCity()))
    var nearbyStopsResult: Result<List<NearbyStop>> = Result.success(emptyList())
    var stopDetailResult: Result<List<NearbyStop>> = Result.success(emptyList())
    var searchResult: Result<TransitSearchResult> = Result.success(TransitSearchResult(keyword = ""))
    var lineDetailResult: Result<BusLineDetail>? = null
    val lineDetailsMap: MutableMap<String, BusLineDetail> = mutableMapOf()
    var timetableResult: Result<LineTimetable> = Result.success(LineTimetable(lineId = "", lineName = ""))
    var favoritesRealtimeResult: Result<Map<String, NearbyStop>> = Result.success(emptyMap())

    /** 为 null 时默认返回当前城市 */
    var resolveCityResult: Result<TransitCity?>? = null

    /** 记录每次附近站点查询的 (城市, 纬度, 经度) */
    val nearbyStopsCalls: MutableList<Triple<TransitCity, Double, Double>> = mutableListOf()

    /** 可按查询坐标返回不同结果；为 null 时统一返回 [nearbyStopsResult] */
    var nearbyStopsResultProvider: ((TransitCity, Double, Double) -> Result<List<NearbyStop>>)? = null

    override suspend fun getSupportedCities(forceRefresh: Boolean): Result<List<TransitCity>> = supportedCitiesResult

    override suspend fun resolveCityByLocation(wgsLat: Double, wgsLng: Double): Result<TransitCity?> =
        resolveCityResult ?: Result.success(currentCity())

    override suspend fun getNearbyStops(
        city: TransitCity,
        wgsLat: Double,
        wgsLng: Double,
        limit: Int
    ): Result<List<NearbyStop>> {
        nearbyStopsCalls += Triple(city, wgsLat, wgsLng)
        return nearbyStopsResultProvider?.invoke(city, wgsLat, wgsLng) ?: nearbyStopsResult
    }

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
    ): Result<List<NearbyStop>> = stopDetailResult

    override suspend fun search(
        city: TransitCity,
        keyword: String,
        userLat: Double?,
        userLng: Double?
    ): Result<TransitSearchResult> = searchResult

    override suspend fun getLineDetail(
        city: TransitCity,
        lineId: String,
        targetOrder: Int?,
        stationId: String?,
        wgsLat: Double?,
        wgsLng: Double?,
        source: DataSourceType,
        lineNameHint: String,
        directionHint: Int
    ): Result<BusLineDetail> {
        lineDetailsMap[lineId]?.let { return Result.success(it) }
        lineDetailResult?.let { return it }
        val direction = BusLineDirection(
            lineId = lineId,
            name = if (lineNameHint.isNotBlank()) lineNameHint else "测试线路",
            startStop = "起点站",
            endStop = "终点站",
            source = source
        )
        return Result.success(BusLineDetail(line = direction, stops = emptyList(), buses = emptyList()))
    }

    override suspend fun getTimetable(
        city: TransitCity,
        line: BusLineDirection,
        atTimeHHMM: String?
    ): Result<LineTimetable> = timetableResult

    override suspend fun refreshFavoritesRealtime(): Result<Map<String, NearbyStop>> = favoritesRealtimeResult
}

/**
 * 定位仓库 Fake：[bestFix] / [lastKnown] 返回测试设定的值，[fixes] 由 [emit] 推送。
 */
class FakeLocationRepository(
    var bestFixResult: UserFix? = null,
    var lastKnownResult: UserFix? = null
) : LocationRepository {

    private val _latest = MutableStateFlow<UserFix?>(null)
    override val latest: StateFlow<UserFix?> = _latest.asStateFlow()

    private val live = MutableSharedFlow<UserFix>(extraBufferCapacity = 64)

    /** 每次 [bestFix] 调用时传的 forceFresh */
    val bestFixCalls: MutableList<Boolean> = mutableListOf()

    override suspend fun bestFix(forceFresh: Boolean): UserFix? {
        bestFixCalls += forceFresh
        return bestFixResult?.also { _latest.value = it }
    }

    override fun lastKnown(maxAgeMillis: Long): UserFix? = lastKnownResult

    override fun fixes(tier: LocationCadence.Tier): Flow<UserFix> =
        if (tier == LocationCadence.Tier.OFF) emptyFlow() else live

    /** 推一个定位给正在收集的 [fixes]，同时更新 [latest] */
    fun emit(fix: UserFix) {
        _latest.value = fix
        live.tryEmit(fix)
    }
}
