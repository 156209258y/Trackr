package com.trackr.ui.screens.favoritesedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.core.data.repository.SettingsRepository
import com.trackr.core.data.route.FavoriteGroupSelection
import com.trackr.core.data.route.FavoritesGroupDataFix
import com.trackr.core.data.route.RouteAlignmentService
import com.trackr.core.data.route.RouteGroupSaver
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.GroupCategory
import com.trackr.core.model.TransitCity
import com.trackr.core.model.TransitGroup
import com.trackr.data.repository.IRouteGroupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.trackr.core.data.route.isGroupInFavoriteGroup as isInFavoriteGroup

/**
 * 编辑收藏的路线与分组（第 17 步从 `FavoritesGroupViewModel` 拆出，逻辑原样搬）：
 * 按场景 / 收藏分组筛选、分组增删改排序、路线删除 / 转存 / 新建 / 生成返程，以及一次性数据修复。
 *
 * 第 17 步旧收藏页（`FavoritesGroupScreen`，第 22 步删）拿它和 `RouteDetailViewModel` 两个；
 * 选中的收藏分组放在共用的 [FavoriteGroupSelection] 里，规划页「存入收藏」没选分组时回落到它（和原来一样）。
 */
class FavoritesEditViewModel(
    private val groupRepository: IRouteGroupRepository,
    private val settings: SettingsRepository,
    private val alignment: RouteAlignmentService,
    private val groupSaver: RouteGroupSaver,
    private val selection: FavoriteGroupSelection,
    /** 一次性数据修复；为 null 时不修（测试用） */
    private val dataFix: FavoritesGroupDataFix? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enableAutoMigration: Boolean = true
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<GroupCategory?>(null)
    val selectedCategory: StateFlow<GroupCategory?> = _selectedCategory.asStateFlow()

    val selectedFavoriteGroupId: StateFlow<String?> = selection.selectedFavoriteGroupId

    val groups: StateFlow<List<TransitGroup>> = groupRepository.groups
    val favoriteGroups: StateFlow<List<FavoriteGroup>> = groupRepository.favoriteGroups

    val currentCity: StateFlow<TransitCity> = settings.selectedCity

    fun isGroupInFavoriteGroup(group: TransitGroup, favGroupId: String): Boolean =
        isInFavoriteGroup(group, favGroupId)

    val filteredGroups: StateFlow<List<TransitGroup>> = combine(
        groups,
        selectedCategory,
        selectedFavoriteGroupId
    ) { allGroups, category, favGroupId ->
        when {
            favGroupId != null -> allGroups.filter { isGroupInFavoriteGroup(it, favGroupId) }
            category != null -> allGroups.filter { it.category == category }
            else -> allGroups
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, groups.value)

    init {
        if (enableAutoMigration) {
            runDataMigrationIfNeeded()
        }
    }

    /**
     * 一次性数据迁移：补齐通勤分组归属、为通勤路线补返程（见 [FavoritesGroupDataFix]），只执行一次。
     * 合成线路的实时对齐不在此处持久化，交由路线的实时刷新在运行时完成。
     */
    fun runDataMigrationIfNeeded(): Job = viewModelScope.launch(ioDispatcher) {
        dataFix?.runIfNeeded()
    }

    fun selectCategory(category: GroupCategory?) {
        _selectedCategory.value = category
        selection.select(null)
    }

    /**
     * 原来选中的分组是 `FavoritesGroupViewModel` 自己的状态，随 Activity 一起清掉；
     * 拆开后放在进程级的 [FavoriteGroupSelection] 里，这里清一下，Activity 重建后不带上一次的选择。
     */
    override fun onCleared() {
        selection.select(null)
        super.onCleared()
    }

    fun selectFavoriteGroup(groupId: String?) {
        selection.select(groupId)
        _selectedCategory.value = null
    }

    fun createFavoriteGroup(name: String) {
        viewModelScope.launch {
            groupRepository.createFavoriteGroup(name)
        }
    }

    fun renameFavoriteGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            groupRepository.renameFavoriteGroup(groupId, newName)
        }
    }

    /** 路线列表拖动排序（第 22 步的编辑收藏页） */
    fun reorderGroups(groupIds: List<String>) {
        viewModelScope.launch {
            groupRepository.reorderGroups(groupIds)
        }
    }

    fun deleteFavoriteGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.deleteFavoriteGroup(groupId)
            if (selection.selectedFavoriteGroupId.value == groupId) {
                selection.select(null)
            }
        }
    }

    fun reorderFavoriteGroups(groupIds: List<String>) {
        viewModelScope.launch {
            groupRepository.reorderFavoriteGroups(groupIds)
        }
    }

    /**
     * 临时追踪的路线转存为正式收藏分组（收藏页「临时追踪」卡上的收藏按钮）
     */
    fun addPlannedSchemeAsFavorite(
        group: TransitGroup,
        customGroupId: String? = null,
        category: GroupCategory = GroupCategory.CUSTOM,
        customName: String? = null
    ) {
        viewModelScope.launch {
            groupSaver.saveAsFavorite(group, customGroupId, category, customName)
        }
    }

    /**
     * 删除分组
     */
    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
        }
    }

    /**
     * 转存临时分组为正式常用分组
     */
    fun saveOrConvertTemporaryGroup(
        groupId: String,
        newName: String? = null,
        newCategory: GroupCategory? = null,
        customGroupId: String? = null
    ) {
        viewModelScope.launch {
            groupRepository.saveOrConvertTemporaryGroup(groupId, newName, newCategory, customGroupId)
        }
    }

    /**
     * 创建或更新自定义分组
     */
    fun saveGroup(group: TransitGroup) {
        viewModelScope.launch {
            groupSaver.saveGroup(group)
        }
    }

    /**
     * 为指定路线生成返程路线
     */
    fun createReverseGroup(group: TransitGroup) {
        viewModelScope.launch {
            val targetGroupId = when (group.customGroupId) {
                "commute" -> "home"
                "home" -> "commute"
                else -> group.customGroupId ?: if (group.category == GroupCategory.COMMUTE) "home" else null
            }
            val reversed = alignment.buildReverseTransitGroup(group, targetGroupId)
            groupRepository.addGroup(reversed)
        }
    }
}
