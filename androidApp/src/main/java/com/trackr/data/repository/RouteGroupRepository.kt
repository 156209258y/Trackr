package com.trackr.data.repository

import android.content.Context
import com.trackr.core.data.local.RouteGroupStore
import com.trackr.data.local.SharedPrefsRouteGroupStore
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.GroupCategory
import com.trackr.core.model.TransitGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 路线分组与自定义收藏的仓库实现。
 *
 * 读写经 [RouteGroupStore]（第 9 步）：USE_NEW_STORE 开时是 Room（route / route_leg / route_candidate_line / favorite_group），
 * 关时是原来的 SharedPreferences `trackr_route_groups`（JSON 损坏先备份原文，148d67c）。业务逻辑两边共用这一份。
 * 构造时同步读一次（StorageBackend 在 IO 线程上建它）；之后每次改动都在 [mutex] 里「改内存 -> 整份写盘」。
 */
class RouteGroupRepository(
    private val store: RouteGroupStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IRouteGroupRepository {

    /** 旧存储（SharedPreferences `trackr_route_groups`）；现有测试和 USE_NEW_STORE 关闭时用 */
    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
        this(SharedPrefsRouteGroupStore(context), ioDispatcher)

    /** 串行化路线分组与收藏分组的所有读改写（deleteFavoriteGroup 同时修改两者，共用一把锁） */
    private val mutex = Mutex()

    private val _groups = MutableStateFlow<List<TransitGroup>>(emptyList())
    override val groups: StateFlow<List<TransitGroup>> = _groups.asStateFlow()

    private val _favoriteGroups = MutableStateFlow<List<FavoriteGroup>>(emptyList())
    override val favoriteGroups: StateFlow<List<FavoriteGroup>> = _favoriteGroups.asStateFlow()

    init {
        runBlocking {
            _groups.value = loadInitialGroups()
            _favoriteGroups.value = loadInitialFavoriteGroups()
        }
    }

    private suspend fun loadInitialGroups(): List<TransitGroup> {
        val loaded = store.loadGroups() ?: return emptyList()
        // 彻底清理历史测试注入的假数据（如 sample_commute_group 或含 sample_line_ 伪造标识的分组）
        val sanitized = loaded.filterNot { group ->
            group.id == "sample_commute_group" ||
                group.legs.any { leg ->
                    leg.candidateLines.any { it.lineId.startsWith("sample_") }
                }
        }
        if (sanitized.size != loaded.size) {
            store.saveGroups(sanitized)
        }
        return sanitized.sortedBy { it.sortOrder }
    }

    private suspend fun loadInitialFavoriteGroups(): List<FavoriteGroup> {
        val loaded = store.loadFavoriteGroups()
        if (!loaded.isNullOrEmpty()) {
            return loaded.sortedBy { it.sortOrder }
        }
        val defaultGroups = listOf(
            FavoriteGroup(id = "commute", name = "上班通勤", sortOrder = 0, isDefault = true),
            FavoriteGroup(id = "home", name = "回家线路", sortOrder = 1, isDefault = true)
        )
        store.saveFavoriteGroups(defaultGroups)
        return defaultGroups
    }

    override suspend fun addGroup(group: TransitGroup) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.id == group.id }
            if (index >= 0) {
                current[index] = group.copy(updatedAt = System.currentTimeMillis())
            } else {
                val maxOrder = current.maxOfOrNull { it.sortOrder } ?: 0
                current.add(group.copy(sortOrder = maxOrder + 1, updatedAt = System.currentTimeMillis()))
            }
            persistGroups(current.sortedBy { it.sortOrder })
        }
    }

    override suspend fun updateGroup(group: TransitGroup) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _groups.value.toMutableList()
            val index = current.indexOfFirst { it.id == group.id }
            if (index >= 0) {
                current[index] = group.copy(updatedAt = System.currentTimeMillis())
                persistGroups(current.sortedBy { it.sortOrder })
            }
        }
    }

    override suspend fun deleteGroup(groupId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _groups.value.filterNot { it.id == groupId }
            persistGroups(current)
        }
    }

    override suspend fun saveOrConvertTemporaryGroup(
        groupId: String,
        newName: String?,
        newCategory: GroupCategory?,
        customGroupId: String?
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _groups.value.map { group ->
                if (group.id == groupId) {
                    group.copy(
                        name = newName?.takeIf { it.isNotBlank() } ?: group.name,
                        category = newCategory ?: if (group.category == GroupCategory.TEMPORARY) GroupCategory.CUSTOM else group.category,
                        customGroupId = customGroupId ?: group.customGroupId,
                        isTemporary = false,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    group
                }
            }
            persistGroups(current)
        }
    }

    override suspend fun reorderGroups(groupOrderIds: List<String>) = withContext(ioDispatcher) {
        mutex.withLock {
            val groupMap = _groups.value.associateBy { it.id }
            val reordered = mutableListOf<TransitGroup>()
            groupOrderIds.forEachIndexed { index, id ->
                groupMap[id]?.let {
                    reordered.add(it.copy(sortOrder = index))
                }
            }
            // 追加任何不在 groupOrderIds 中的遗留分组
            _groups.value.filterNot { it.id in groupOrderIds }.forEachIndexed { extraIndex, group ->
                reordered.add(group.copy(sortOrder = groupOrderIds.size + extraIndex))
            }
            persistGroups(reordered)
        }
    }

    override fun getGroupById(groupId: String): TransitGroup? {
        return _groups.value.find { it.id == groupId }
    }

    override suspend fun createFavoriteGroup(name: String): FavoriteGroup = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _favoriteGroups.value.toMutableList()
            val maxOrder = current.maxOfOrNull { it.sortOrder } ?: -1
            val newGroup = FavoriteGroup(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                sortOrder = maxOrder + 1,
                isDefault = false
            )
            current.add(newGroup)
            persistFavoriteGroups(current.sortedBy { it.sortOrder })
            newGroup
        }
    }

    override suspend fun renameFavoriteGroup(groupId: String, newName: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _favoriteGroups.value.toMutableList()
            val index = current.indexOfFirst { it.id == groupId }
            if (index >= 0) {
                current[index] = current[index].copy(name = newName.trim())
                persistFavoriteGroups(current.sortedBy { it.sortOrder })
            }
        }
    }

    override suspend fun deleteFavoriteGroup(groupId: String) = withContext(ioDispatcher) {
        mutex.withLock {
            // 1. 从收藏分组列表中移除
            val updatedFavoriteGroups = _favoriteGroups.value.filterNot { it.id == groupId }

            // 2. 属于该分组的路线不删除，仅解除 customGroupId 关联归入未分组
            val currentGroups = _groups.value.toMutableList()
            var changed = false
            for (i in currentGroups.indices) {
                if (currentGroups[i].customGroupId == groupId) {
                    currentGroups[i] = currentGroups[i].copy(
                        customGroupId = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    changed = true
                }
            }
            // 两样一起写：新存储里是一个事务（§2.4「多步改动用 Room 事务」）
            _favoriteGroups.value = updatedFavoriteGroups
            if (changed) {
                _groups.value = currentGroups
                store.saveGroupsAndFavoriteGroups(currentGroups, updatedFavoriteGroups)
            } else {
                store.saveFavoriteGroups(updatedFavoriteGroups)
            }
        }
    }

    override suspend fun reorderFavoriteGroups(groupIds: List<String>) = withContext(ioDispatcher) {
        mutex.withLock {
            val groupMap = _favoriteGroups.value.associateBy { it.id }
            val reordered = mutableListOf<FavoriteGroup>()
            groupIds.forEachIndexed { index, id ->
                groupMap[id]?.let {
                    reordered.add(it.copy(sortOrder = index))
                }
            }
            _favoriteGroups.value.filterNot { it.id in groupIds }.forEachIndexed { extraIndex, group ->
                reordered.add(group.copy(sortOrder = groupIds.size + extraIndex))
            }
            persistFavoriteGroups(reordered)
        }
    }

    override suspend fun addCandidateLineToLeg(
        groupId: String,
        legIndex: Int,
        candidateLine: CandidateLine
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            val currentGroups = _groups.value.toMutableList()
            val groupIndex = currentGroups.indexOfFirst { it.id == groupId }
            if (groupIndex >= 0) {
                val group = currentGroups[groupIndex]
                val updatedLegs = group.legs.map { leg ->
                    if (leg.legIndex == legIndex) {
                        val alreadyHas = leg.candidateLines.any { it.lineId == candidateLine.lineId }
                        if (alreadyHas) {
                            leg
                        } else {
                            leg.copy(candidateLines = leg.candidateLines + candidateLine)
                        }
                    } else {
                        leg
                    }
                }
                currentGroups[groupIndex] = group.copy(
                    legs = updatedLegs,
                    updatedAt = System.currentTimeMillis()
                )
                persistGroups(currentGroups)
            }
        }
    }

    private suspend fun persistGroups(list: List<TransitGroup>) {
        _groups.value = list
        store.saveGroups(list)
    }

    private suspend fun persistFavoriteGroups(list: List<FavoriteGroup>) {
        _favoriteGroups.value = list
        store.saveFavoriteGroups(list)
    }

    companion object {
        /** 旧存储里损坏 JSON 的备份 key 后缀（实现在 [SharedPrefsRouteGroupStore]） */
        internal const val CORRUPT_BACKUP_SUFFIX = SharedPrefsRouteGroupStore.CORRUPT_BACKUP_SUFFIX
    }
}
