package com.trackr.core.data.repository

import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.AmapTipItem
import com.trackr.core.model.AmapTransitSchemeResult
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
import kotlinx.coroutines.flow.StateFlow

/*
 * 原来的上帝接口 ITransitRepository 按职责拆成下面几个（重构计划 §2.5）。
 *
 * 第 8 步：:androidApp 的 TransitRepository 先同时实现设置 / 收藏 / 实时查询 / 搜索历史四个接口
 * （实时查询那部分委托给 commonMain 的 [DefaultRealtimeRepository]），定位由 [DefaultLocationRepository] 实现；
 * ViewModel 仍然拿 ITransitRepository，第 17 步搬进各 feature 时改成只拿要用的接口。
 * 第 9 步：存储换成 DataStore / Room（`local/` 下的 Store），由运行时开关 USE_NEW_STORE 选新旧实现，仓库逻辑只有一份。
 */

/**
 * 设置：城市、数据源、乘车码渠道、附近站数量、实时通知开关。
 * 高德 Key 两槽和由它算出的 `hasMapKey` / `canShowMap`（§2.5）在 [AmapKeyRepository]。
 */
interface SettingsRepository {
    val selectedCity: StateFlow<TransitCity>

    /** 用户是否曾经选过/存过城市（首次安装为 false，此时才允许定位自动切换城市）。第 9 步起是 Flow */
    val hasSavedCity: StateFlow<Boolean>
    /** 实时数据源，二选一（D1） */
    val engineMode: StateFlow<EngineMode>
    val preferredTransitCodeProvider: StateFlow<TransitCodeProvider>
    val nearbyStopsCount: StateFlow<Int>
    val isLiveNotificationEnabled: StateFlow<Boolean>

    /** 追踪通知在锁屏显示全部内容（D3），默认开；关了锁屏只显示「追客 · 追踪中」 */
    val showOnLockScreen: StateFlow<Boolean>

    fun setEngineMode(mode: EngineMode)
    fun setNearbyStopsCount(count: Int)
    fun setLiveNotificationEnabled(enabled: Boolean)
    fun setShowOnLockScreen(enabled: Boolean)
    suspend fun selectCity(city: TransitCity)
    suspend fun setPreferredTransitCodeProvider(provider: TransitCodeProvider)
}

/** 收藏的车（站 + 线）。路线与分组合进来、`move` / `setGroup` 在第 14 步 */
interface FavoritesRepository {
    val favorites: StateFlow<List<FavoriteStationLine>>

    /** 有就删、没有就加到最前；返回 true 表示这次是加 */
    suspend fun toggleFavorite(item: FavoriteStationLine): Boolean
    suspend fun removeFavorite(id: String)
}

/** 搜索历史：最近的在前，最多 20 条，不分大小写去重 */
interface SearchHistoryRepository {
    val searchHistory: StateFlow<List<String>>

    suspend fun addSearchHistory(query: String)
    suspend fun removeSearchHistory(query: String)
    suspend fun clearSearchHistory()
}

/**
 * 实时查询：城市列表、定位城市、附近、站点、线路、时刻表、搜索；按当前数据源选 Provider。
 * 线路详情按 key 合并并发请求、缓存 6 秒；`getStopDetail` / `getLineDetail` 被取消时照常抛出取消，不包成失败结果。
 */
interface RealtimeRepository {
    suspend fun getSupportedCities(forceRefresh: Boolean = false): Result<List<TransitCity>>
    suspend fun resolveCityByLocation(wgsLat: Double, wgsLng: Double): Result<TransitCity?>

    suspend fun getNearbyStops(
        city: TransitCity,
        wgsLat: Double,
        wgsLng: Double,
        limit: Int = 4
    ): Result<List<NearbyStop>>

    suspend fun getStopDetail(
        city: TransitCity,
        stopId: String? = null,
        stopName: String? = null,
        physicalStId: String? = null,
        namesakeStId: String? = null,
        stationLat: Double? = null,
        stationLng: Double? = null,
        userLat: Double? = null,
        userLng: Double? = null
    ): Result<List<NearbyStop>>

    /** 有结果时把关键字记进搜索历史 */
    suspend fun search(
        city: TransitCity,
        keyword: String,
        userLat: Double? = null,
        userLng: Double? = null
    ): Result<TransitSearchResult>

    suspend fun getLineDetail(
        city: TransitCity,
        lineId: String,
        targetOrder: Int? = null,
        stationId: String? = null,
        wgsLat: Double? = null,
        wgsLng: Double? = null,
        source: DataSourceType = if (lineId.startsWith("mg:")) DataSourceType.MYGOLBS else DataSourceType.CHELAILE,
        lineNameHint: String = "",
        directionHint: Int = 0
    ): Result<BusLineDetail>

    suspend fun getTimetable(
        city: TransitCity,
        line: BusLineDirection,
        atTimeHHMM: String? = null
    ): Result<LineTimetable>

    /** 收藏的每条线各查一次线路详情（命中 6 秒合并缓存），返回 收藏 id -> 站点 */
    suspend fun refreshFavoritesRealtime(): Result<Map<String, NearbyStop>>
}

/**
 * 手机定位（WGS-84），都带精度和单调时刻（[UserFix]）。实现经 `LocationSource`（Android / iOS 各一份）。
 */
interface LocationRepository {

    /** 最近一次拿到的定位（[bestFix] 的结果、[fixes] 收到的每个定位） */
    val latest: StateFlow<UserFix?>

    /**
     * 首页取一次定位：lastKnown 够新（默认 60 秒，[forceFresh] 时 10 秒）且精度 ≤100 m 就直接用；
     * 否则在 4.5 秒窗口内监听，拿到 ≤100 m 的提前结束，窗口结束取最准的；
     * 什么都没拿到时退回 10 分钟内最新的旧定位。没有定位权限时返回 null。
     */
    suspend fun bestFix(forceFresh: Boolean): UserFix?

    /** 系统缓存的定位里 [maxAgeMillis] 内、精度最高的一个（路线规划「我的位置」兜底）；没有权限或没有时返回 null */
    fun lastKnown(maxAgeMillis: Long): UserFix?

    /** 按档位持续定位（追踪用），收到的每个定位都会更新 [latest] */
    fun fixes(tier: LocationCadence.Tier): Flow<UserFix>
}

/**
 * 高德输入提示 / 公交算路通道。
 *
 * Android 实现（现在在 `FavoritesGroupViewModel` 里）：SDK 优先，**只有 SDK 调用失败**才回落 Web 服务；
 * SDK 成功但结果为空时不再重复请求 Web。iOS 只走 Web。单元测试可注入替身，避免发起真实请求。
 */
interface AmapRouteGateway {
    /** 是否至少配置了一条可用通道（Android Key 或 Web 服务 Key） */
    fun isConfigured(): Boolean

    /** @param location 当前位置 "lng,lat"（GCJ-02），可为空 */
    suspend fun searchTips(keyword: String, cityName: String, location: String?): Result<List<AmapTipItem>>

    /** @param cityd 跨城时的终点城市 adcode，同城传 null */
    suspend fun planTransit(
        originLngLat: String,
        destinationLngLat: String,
        originName: String,
        destName: String,
        cityName: String,
        cityd: String?
    ): Result<List<AmapTransitSchemeResult>>
}

/**
 * 路线规划：高德输入提示（LRU 64）、公交算路（LRU 16，键 = 城市 + 6 位小数坐标 + 15 分钟时间桶）、
 * 方案与实时线路对齐（命中备忘 24 小时 / 没命中 10 分钟，并发 4），后面是 [AmapRouteGateway]。
 *
 * 第 8 步只定接口：这些缓存和对齐现在都还在 `FavoritesGroupViewModel` 里，
 * 第 17 步拆 VM 时把它们搬进本接口的实现（重构计划 §2.5、§2.10）。
 */
interface RoutePlanRepository {
    fun isConfigured(): Boolean

    /** @param location 当前位置 "lng,lat"（GCJ-02），可为空 */
    suspend fun searchTips(keyword: String, city: TransitCity, location: String?): Result<List<AmapTipItem>>

    suspend fun planTransit(
        origin: AmapTipItem,
        destination: AmapTipItem,
        city: TransitCity
    ): Result<List<AmapTransitSchemeResult>>
}
