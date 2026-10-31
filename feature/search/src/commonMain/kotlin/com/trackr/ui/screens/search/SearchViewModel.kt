package com.trackr.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.core.data.repository.HomeCoordinates
import com.trackr.core.data.repository.RealtimeRepository
import com.trackr.core.data.repository.SearchHistoryRepository
import com.trackr.core.data.repository.SettingsRepository
import com.trackr.core.data.repository.UserLocationStore
import com.trackr.core.model.ProvinceCityCatalog
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    /** 已选城市（搜索框提示文字、结果里的城市 id） */
    val currentCity: TransitCity = TransitCity(cityId = "034", cityName = "上海", pinyin = "shanghai", supportSubway = true, isHot = true, province = "直辖市"),
    val searchQuery: String = "",
    val searchResult: TransitSearchResult? = null,
    val isSearching: Boolean = false,
    val searchHistory: List<String> = emptyList()
)

/**
 * 线路 / 站点搜索（第 17 步从 HomeViewModel 拆出来，逻辑照搬）。
 *
 * 取 Activity 级实例（`LocalAppViewModelStoreOwner`），退出搜索页再进来关键词和结果还在，和拆分前一样。
 */
class SearchViewModel(
    private val settings: SettingsRepository,
    private val realtime: RealtimeRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    /** 首页定位写入的最近 GPS 坐标，作为搜索结果的距离 / 排序参考 */
    private val userLocationStore: UserLocationStore,
    /** 首页当前坐标（拆分前搜索读的就是它）；为 null（测试）时按 GPS → 城市中心点 */
    private val homeCoordinates: HomeCoordinates? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(currentCity = settings.selectedCity.value))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // 观察已选城市：城市变了就清掉旧结果（原 HomeViewModel.selectCity 里清 searchResult）
        viewModelScope.launch {
            settings.selectedCity.collect { city ->
                _uiState.update {
                    if (it.currentCity == city) it else it.copy(currentCity = city, searchResult = null)
                }
            }
        }

        // 观察搜索历史
        viewModelScope.launch {
            searchHistoryRepository.searchHistory.collect { history ->
                _uiState.update { it.copy(searchHistory = history) }
            }
        }

        // 数据源变了：有关键词就按新数据源重搜
        viewModelScope.launch {
            settings.engineMode.collect {
                val currentQuery = _uiState.value.searchQuery
                if (currentQuery.isNotBlank()) {
                    updateSearchQuery(currentQuery)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResult = null, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS) // 300ms 防抖
            _uiState.update { it.copy(isSearching = true) }
            val city = settings.selectedCity.value
            val (lat, lng) = searchCoordinates(city)
            val res = realtime.search(
                city = city,
                keyword = query.trim(),
                userLat = lat.takeIf { it > 0.0 },
                userLng = lng.takeIf { it > 0.0 }
            )
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResult = res.getOrNull()
                )
            }
        }
    }

    /**
     * 搜索带的参考坐标：和拆分前一样用首页的 currentLat/currentLng（GPS，人在外地时是已选城市中心点），
     * 由首页写进 [HomeCoordinates]。没有首页坐标时（测试）用 GPS，再没有就用已选城市中心点（未收录的城市回落上海）。
     */
    private fun searchCoordinates(city: TransitCity): Pair<Double, Double> =
        homeCoordinates?.value?.value
            ?: userLocationStore.userLocation.value
            ?: ProvinceCityCatalog.getCityCenterCoordinates(city.cityName)

    fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searchHistoryRepository.addSearchHistory(query.trim())
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.removeSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearSearchHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
