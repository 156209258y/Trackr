package com.trackr.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.trackr.core.data.provider.TransitProviderCoordinator
import com.trackr.core.data.repository.DefaultRealtimeRepository
import com.trackr.core.data.repository.PlatformGeocoder
import com.trackr.core.domain.platform.MonotonicClock
import com.trackr.core.domain.platform.ProcessMonotonicClock
import com.trackr.core.model.AggregatedSourcePriority
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.EngineMode
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.LineTimetable
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.ProvinceCityCatalog
import com.trackr.core.model.TrackingSessionState
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.model.TransitSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 统一实时公交数据仓库实现（过渡用，第 9 步换存储）。
 *
 * 设置、收藏、搜索历史仍存在 SharedPreferences `trackr_prefs`；
 * 实时查询（附近、站点、线路、时刻表、搜索、定位城市）委托给 commonMain 的 [DefaultRealtimeRepository]。
 */
class TransitRepository(
    private val context: Context,
    val coordinator: TransitProviderCoordinator,
    /** 单调时钟，用于线路详情短缓存；测试可注入 */
    clock: MonotonicClock = ProcessMonotonicClock,
    /** 线路详情合并请求的执行作用域；测试可注入 */
    fetchScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** 数据源逆地理拿不到城市时的兜底；默认用系统 Geocoder */
    geocoder: PlatformGeocoder = AndroidGeocoder(context)
) : ITransitRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("trackr_prefs", Context.MODE_PRIVATE)

    private val _selectedCity = MutableStateFlow(loadInitialCity())
    override val selectedCity: StateFlow<TransitCity> = _selectedCity.asStateFlow()
    override val hasSavedCity: Boolean
        get() = prefs.contains("selected_city_json")

    private val _engineMode = MutableStateFlow(loadInitialEngineMode())
    override val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _aggregatedPriority = MutableStateFlow(loadInitialAggregatedPriority())
    override val aggregatedPriority: StateFlow<AggregatedSourcePriority> = _aggregatedPriority.asStateFlow()

    private val _favorites = MutableStateFlow(loadInitialFavorites())
    override val favorites: StateFlow<List<FavoriteStationLine>> = _favorites.asStateFlow()

    private val _searchHistory = MutableStateFlow(loadInitialSearchHistory())
    override val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _preferredTransitCodeProvider = MutableStateFlow(loadInitialTransitCodeProvider())
    override val preferredTransitCodeProvider: StateFlow<TransitCodeProvider> = _preferredTransitCodeProvider.asStateFlow()

    private val _nearbyStopsCount = MutableStateFlow(loadInitialNearbyStopsCount())
    override val nearbyStopsCount: StateFlow<Int> = _nearbyStopsCount.asStateFlow()

    private val _isLiveNotificationEnabled = MutableStateFlow(loadInitialLiveNotificationEnabled())
    override val isLiveNotificationEnabled: StateFlow<Boolean> = _isLiveNotificationEnabled.asStateFlow()

    private val _activeTrackingSession = MutableStateFlow(TrackingSessionState())
    override val activeTrackingSession: StateFlow<TrackingSessionState> = _activeTrackingSession.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    override val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    override fun updateUserLocation(lat: Double, lng: Double) {
        if (lat > 0.0 && lng > 0.0) {
            _userLocation.value = Pair(lat, lng)
        }
    }

    /** 实时查询部分（commonMain） */
    private val realtime = DefaultRealtimeRepository(
        coordinator = coordinator,
        settings = this,
        favoritesRepository = this,
        searchHistory = this,
        userLocation = { _userLocation.value },
        geocoder = geocoder,
        clock = clock,
        fetchScope = fetchScope
    )

    private fun loadInitialCity(): TransitCity {
        val cityJson = prefs.getString("selected_city_json", null)
        if (!cityJson.isNullOrBlank()) {
            try {
                val city = json.decodeFromString<TransitCity>(cityJson)
                val prov = if (city.province.isNotBlank()) city.province else ProvinceCityCatalog.inferProvince(city.cityName)
                return city.copy(province = prov)
            } catch (_: Exception) {}
        }
        return TransitCity(cityId = "034", cityName = "上海", pinyin = "ShangHai", supportSubway = true, isHot = true, province = "直辖市")
    }

    private fun loadInitialFavorites(): List<FavoriteStationLine> {
        val favJson = prefs.getString("favorites_json", null)
        if (!favJson.isNullOrBlank()) {
            try {
                return json.decodeFromString<List<FavoriteStationLine>>(favJson)
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadInitialSearchHistory(): List<String> {
        val historyJson = prefs.getString("search_history_json", null)
        if (!historyJson.isNullOrBlank()) {
            try {
                return json.decodeFromString<List<String>>(historyJson)
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun loadInitialTransitCodeProvider(): TransitCodeProvider {
        val providerId = prefs.getString("preferred_transit_code_provider", TransitCodeProvider.ASK_ALWAYS.id)
        return TransitCodeProvider.fromId(providerId)
    }

    private fun loadInitialNearbyStopsCount(): Int {
        return prefs.getInt("nearby_stops_count", 4).coerceIn(1, 16)
    }

    override fun setNearbyStopsCount(count: Int) {
        val clamped = count.coerceIn(1, 16)
        _nearbyStopsCount.value = clamped
        prefs.edit().putInt("nearby_stops_count", clamped).apply()
    }

    private fun loadInitialLiveNotificationEnabled(): Boolean {
        return prefs.getBoolean("is_live_notification_enabled", true)
    }

    override fun setLiveNotificationEnabled(enabled: Boolean) {
        _isLiveNotificationEnabled.value = enabled
        prefs.edit().putBoolean("is_live_notification_enabled", enabled).apply()
    }

    private fun loadInitialEngineMode(): EngineMode {
        val modeStr = prefs.getString("engine_mode", EngineMode.CHELAILE.name)
        return runCatching { EngineMode.valueOf(modeStr ?: EngineMode.CHELAILE.name) }.getOrDefault(EngineMode.CHELAILE)
    }

    private fun loadInitialAggregatedPriority(): AggregatedSourcePriority {
        val name = prefs.getString("aggregated_source_priority", AggregatedSourcePriority.CHELAILE_FIRST.name)
        return runCatching { AggregatedSourcePriority.valueOf(name ?: AggregatedSourcePriority.CHELAILE_FIRST.name) }
            .getOrDefault(AggregatedSourcePriority.CHELAILE_FIRST)
    }

    override fun setAggregatedPriority(priority: AggregatedSourcePriority) {
        _aggregatedPriority.value = priority
        prefs.edit().putString("aggregated_source_priority", priority.name).apply()
    }

    override fun setEngineMode(mode: EngineMode) {
        _engineMode.value = mode
        prefs.edit().putString("engine_mode", mode.name).apply()
        // 若当前城市缺少掌上公交编码，尝试从缓存城市列表中补齐
        val currCity = _selectedCity.value
        val cachedCities = realtime.cachedCities
        if (currCity.mygolbsCityCode.isNullOrBlank() && cachedCities.isNotEmpty()) {
            val matched = cachedCities.firstOrNull {
                ProvinceCityCatalog.cleanCityName(it.cityName) == ProvinceCityCatalog.cleanCityName(currCity.cityName)
            }
            if (matched?.mygolbsCityCode?.isNotBlank() == true) {
                _selectedCity.value = currCity.copy(mygolbsCityCode = matched.mygolbsCityCode)
            }
        }
    }

    private fun saveFavorites(favs: List<FavoriteStationLine>) {
        val str = json.encodeToString(favs)
        prefs.edit().putString("favorites_json", str).apply()
    }

    private fun saveSearchHistory(history: List<String>) {
        val str = json.encodeToString(history)
        prefs.edit().putString("search_history_json", str).apply()
    }

    override suspend fun selectCity(city: TransitCity) {
        val effectiveCity = if (city.province.isBlank()) {
            city.copy(province = ProvinceCityCatalog.inferProvince(city.cityName))
        } else {
            city
        }
        _selectedCity.value = effectiveCity
        withContext(Dispatchers.IO) {
            val str = json.encodeToString(effectiveCity)
            prefs.edit().putString("selected_city_json", str).apply()
        }
    }

    override suspend fun getSupportedCities(forceRefresh: Boolean): Result<List<TransitCity>> =
        realtime.getSupportedCities(forceRefresh)

    override suspend fun resolveCityByLocation(wgsLat: Double, wgsLng: Double): Result<TransitCity?> =
        realtime.resolveCityByLocation(wgsLat, wgsLng)

    override suspend fun getNearbyStops(
        city: TransitCity,
        wgsLat: Double,
        wgsLng: Double,
        limit: Int
    ): Result<List<NearbyStop>> = realtime.getNearbyStops(city, wgsLat, wgsLng, limit)

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
    ): Result<List<NearbyStop>> = realtime.getStopDetail(
        city, stopId, stopName, physicalStId, namesakeStId, stationLat, stationLng, userLat, userLng
    )

    override suspend fun search(
        city: TransitCity,
        keyword: String,
        userLat: Double?,
        userLng: Double?
    ): Result<TransitSearchResult> = realtime.search(city, keyword, userLat, userLng)

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
    ): Result<BusLineDetail> = realtime.getLineDetail(
        city, lineId, targetOrder, stationId, wgsLat, wgsLng, source, lineNameHint, directionHint
    )

    override suspend fun getTimetable(
        city: TransitCity,
        line: BusLineDirection,
        atTimeHHMM: String?
    ): Result<LineTimetable> = realtime.getTimetable(city, line, atTimeHHMM)

    override suspend fun refreshFavoritesRealtime(): Result<Map<String, NearbyStop>> =
        realtime.refreshFavoritesRealtime()

    override suspend fun toggleFavorite(item: FavoriteStationLine): Boolean {
        val current = _favorites.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        val isAdded: Boolean
        if (index >= 0) {
            current.removeAt(index)
            isAdded = false
        } else {
            current.add(0, item)
            isAdded = true
        }
        _favorites.value = current
        withContext(Dispatchers.IO) {
            saveFavorites(current)
        }
        return isAdded
    }

    override suspend fun removeFavorite(id: String) {
        val current = _favorites.value.toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            _favorites.value = current
            withContext(Dispatchers.IO) {
                saveFavorites(current)
            }
        }
    }

    override suspend fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val limited = if (current.size > MAX_SEARCH_HISTORY) current.take(MAX_SEARCH_HISTORY) else current
        _searchHistory.value = limited
        withContext(Dispatchers.IO) {
            saveSearchHistory(limited)
        }
    }

    override suspend fun removeSearchHistory(query: String) {
        val trimmed = query.trim()
        val current = _searchHistory.value.toMutableList()
        val removed = current.removeAll { it.equals(trimmed, ignoreCase = true) }
        if (removed) {
            _searchHistory.value = current
            withContext(Dispatchers.IO) {
                saveSearchHistory(current)
            }
        }
    }

    override suspend fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        withContext(Dispatchers.IO) {
            prefs.edit().remove("search_history_json").apply()
        }
    }

    override suspend fun setPreferredTransitCodeProvider(provider: TransitCodeProvider) {
        _preferredTransitCodeProvider.value = provider
        withContext(Dispatchers.IO) {
            prefs.edit().putString("preferred_transit_code_provider", provider.id).apply()
        }
    }

    override fun updateTrackingSession(state: TrackingSessionState) {
        _activeTrackingSession.value = state
    }

    companion object {
        const val MAX_SEARCH_HISTORY = 20

        /** 线路详情短缓存时长（实现搬到了 [DefaultRealtimeRepository]） */
        const val LINE_DETAIL_CACHE_TTL_MS = DefaultRealtimeRepository.LINE_DETAIL_CACHE_TTL_MS
    }
}

/** 系统 Geocoder 兜底（原来写在 `resolveCityByLocation` 里） */
private class AndroidGeocoder(context: Context) : PlatformGeocoder {
    private val appContext: Context = context.applicationContext ?: context

    override suspend fun locate(wgsLat: Double, wgsLng: Double): Pair<String?, String?>? {
        if (!android.location.Geocoder.isPresent()) return null
        @Suppress("DEPRECATION")
        val address = android.location.Geocoder(appContext, java.util.Locale.CHINA)
            .getFromLocation(wgsLat, wgsLng, 1)
            ?.firstOrNull()
            ?: return null
        return (address.locality ?: address.subAdminArea ?: address.adminArea) to address.subLocality
    }
}
