package com.trackr.core.network.provider.mygolbs

import com.trackr.core.domain.math.RouteProjectionMath
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.LineTimetable
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitSearchResult
import com.trackr.core.model.isPureSubwayStation
import com.trackr.core.network.provider.BaseTransitProvider
import com.trackr.core.network.provider.model.LineDetailRequest
import com.trackr.core.network.provider.model.StopDetailRequest
import com.trackr.core.network.remote.mygolbs.MygolbsApiClient
import com.trackr.core.network.remote.mygolbs.MygolbsIds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 掌上公交（Mygolbs）数据源 Provider
 * 内聚 mg: 协议识别、站名清洗、并发检索、站点坐标/距离补齐与发车时刻表
 */
class MygolbsTransitProvider(
    private val mygolbsClient: MygolbsApiClient
) : BaseTransitProvider() {

    override val sourceType: DataSourceType = DataSourceType.MYGOLBS

    override fun ownsId(id: String): Boolean = MygolbsIds.isMygolbsId(id)

    /** 站点接口只给「x 站」文字，不带给车辆站序，一起等要按线路补查 */
    override fun stopDetailIncludesBuses(): Boolean = false

    override suspend fun loadSupportedCities(): List<TransitCity> {
        return mygolbsClient.getCities().map { (name, code) ->
            TransitCity(
                cityId = "",
                cityName = name,
                mygolbsCityCode = code
            )
        }
    }

    override suspend fun resolveCityByLocation(wgsLat: Double, wgsLng: Double): String? {
        return mygolbsClient.reverseGeocodeCityName(wgsLat, wgsLng)
    }

    override suspend fun search(
        city: TransitCity,
        keyword: String,
        userLat: Double?,
        userLng: Double?
    ): TransitSearchResult = coroutineScope {
        val linesDeferred = async {
            runCatching {
                mygolbsClient.searchLines(
                    cityCode = city.mygolbsCityCode.orEmpty(),
                    cityName = city.cityName,
                    keyword = keyword
                )
            }.getOrDefault(emptyList())
        }

        val stopsDeferred = async {
            runCatching {
                mygolbsClient.searchStops(
                    cityName = city.cityName,
                    keyword = keyword
                )
            }.getOrDefault(emptyList())
        }

        // 车来了搜索会给站点距离，掌上公交以前不给；搜索结果右侧的距离因此只有车来了能显示
        val stops = stopsDeferred.await().map { stop ->
            withDistance(stop, userLat, userLng)
        }
        TransitSearchResult(
            keyword = keyword,
            lines = linesDeferred.await(),
            stops = stops
        )
    }

    override suspend fun getNearbyStops(
        city: TransitCity,
        wgsLat: Double,
        wgsLng: Double,
        limit: Int
    ): List<NearbyStop> {
        return mygolbsClient.getNearbyStops(city.cityName, wgsLat, wgsLng, limit)
    }

    override suspend fun getStopDetail(
        city: TransitCity,
        request: StopDetailRequest
    ): List<NearbyStop> {
        val rawName = request.stopName
            ?: MygolbsIds.stopNameOf(request.stopId)
            ?: MygolbsIds.stopNameOf(request.physicalStId)
            ?: MygolbsIds.stopNameOf(request.namesakeStId)
            ?: ""

        val cleanName = MygolbsIds.cleanStationName(rawName)
        if (cleanName.isBlank()) {
            return emptyList()
        }

        val stop = runCatching {
            mygolbsClient.getStopLines(city.cityName, cleanName)
        }.getOrNull()

        if (stop == null || stop.isPureSubwayStation) {
            return emptyList()
        }

        // 掌上公交的站点接口不带站点坐标；调用方本来就把坐标传进来了，直接用，
        // 并顺手按用户位置算距离。以前这里返回 0,0，页面会显示到几内亚湾的「上万公里」。
        val stationLat = request.stationLat?.takeIf { it > 0.0 } ?: stop.wgsLat
        val stationLng = request.stationLng?.takeIf { it > 0.0 } ?: stop.wgsLng
        return listOf(
            stop.copy(
                wgsLat = stationLat,
                wgsLng = stationLng,
                distanceMeters = resolveDistance(
                    serverDistance = stop.distanceMeters,
                    userLat = request.userLat,
                    userLng = request.userLng,
                    stationLat = stationLat,
                    stationLng = stationLng
                )
            )
        )
    }

    override suspend fun getLineDetail(
        city: TransitCity,
        request: LineDetailRequest
    ): BusLineDetail {
        return mygolbsClient.getLineDetail(request.lineId, request.targetOrder)
    }

    override suspend fun getTimetable(
        city: TransitCity,
        line: BusLineDirection,
        atTimeHHMM: String?
    ): LineTimetable {
        return mygolbsClient.getTimetable(
            cityCode = city.mygolbsCityCode.orEmpty(),
            cityName = city.cityName,
            line = line,
            atTimeHHMM = atTimeHHMM
        )
    }

    private fun withDistance(stop: NearbyStop, userLat: Double?, userLng: Double?): NearbyStop {
        val distance = resolveDistance(
            serverDistance = stop.distanceMeters,
            userLat = userLat,
            userLng = userLng,
            stationLat = stop.wgsLat,
            stationLng = stop.wgsLng
        )
        return if (distance == stop.distanceMeters) stop else stop.copy(distanceMeters = distance)
    }

    private fun resolveDistance(
        serverDistance: Int?,
        userLat: Double?,
        userLng: Double?,
        stationLat: Double,
        stationLng: Double
    ): Int? {
        if (serverDistance != null && serverDistance > 0) return serverDistance
        if (userLat == null || userLng == null) return null
        if (userLat <= 0.0 || userLng <= 0.0 || stationLat <= 0.0 || stationLng <= 0.0) return null
        return RouteProjectionMath.calculateDistanceMeters(userLat, userLng, stationLat, stationLng)
    }
}
