package com.trackr.core.network.provider

import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.LineTimetable
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitSearchResult
import com.trackr.core.network.provider.model.LineDetailRequest
import com.trackr.core.network.provider.model.StopDetailRequest

/**
 * 实时公交统一数据源 Provider 标准契约
 */
interface ITransitProvider {
    val sourceType: DataSourceType

    fun supportsLine(lineId: String): Boolean
    fun supportsStop(request: StopDetailRequest): Boolean
    fun supportsLocationRadar(): Boolean

    suspend fun getSupportedCities(): List<TransitCity>
    suspend fun resolveCityByLocation(wgsLat: Double, wgsLng: Double): String?
    /** 逆地理区县名（未清洗，如 "北流市"、"玉州区"）；不支持的数据源返回 null */
    suspend fun resolveDistrictByLocation(wgsLat: Double, wgsLng: Double): String? = null
    suspend fun search(
        city: TransitCity,
        keyword: String,
        userLat: Double? = null,
        userLng: Double? = null
    ): TransitSearchResult
    suspend fun getNearbyStops(
        city: TransitCity,
        wgsLat: Double,
        wgsLng: Double,
        limit: Int = 4
    ): List<NearbyStop>
    suspend fun getStopDetail(
        city: TransitCity,
        request: StopDetailRequest
    ): List<NearbyStop>
    suspend fun getLineDetail(
        city: TransitCity,
        request: LineDetailRequest
    ): BusLineDetail
    suspend fun getTimetable(
        city: TransitCity,
        line: BusLineDirection,
        atTimeHHMM: String? = null
    ): LineTimetable
}
