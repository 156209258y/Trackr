package com.trackr.data.repository

import com.trackr.core.data.repository.FavoritesRepository
import com.trackr.core.data.repository.RealtimeRepository
import com.trackr.core.data.repository.SearchHistoryRepository
import com.trackr.core.data.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 统一实时公交数据仓库契约（过渡用）。
 *
 * 第 8 步把原来的上帝接口拆成 `:core:data` 里的 [SettingsRepository]、[FavoritesRepository]、
 * [RealtimeRepository]、[SearchHistoryRepository]（定位另有 `LocationRepository`），这里只是把它们合在一起，
 * 让现有 ViewModel 先不用改；第 17 步 ViewModel 搬进各 feature 时改成只拿要用的接口，这个接口随之删掉。
 */
interface ITransitRepository : SettingsRepository, FavoritesRepository, RealtimeRepository, SearchHistoryRepository {

    /**
     * 首页定位写入的最近坐标（WGS-84），站点详情 / 搜索 / 规划页「我的位置」没传坐标时用。
     * 第 17 步 ViewModel 改读 `LocationRepository.latest` 后删掉。
     */
    val userLocation: StateFlow<Pair<Double, Double>?>

    fun updateUserLocation(lat: Double, lng: Double)
}
