package com.trackr.core.data.local

import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * 仓库下面的「存储层」（第 9 步）。
 *
 * 仓库的业务逻辑（加收藏放最前、搜索历史去重 20 条、清理假数据、默认分组、删分组后路线变不分组……）只有一份，
 * 存储有两套实现，由运行时开关 USE_NEW_STORE 选：
 * - 新：Room（列表类）+ DataStore（设置、高德 Key），本模块 `Room*Store` / `DataStore*Store`；
 * - 旧：原来的 SharedPreferences + JSON，在 :androidApp 的 `SharedPrefs*Store`，开关关掉时用（风险 R1）。
 *
 * 所有方法都是挂起函数；仓库在构造时读一次（`load*`），之后每次改动整份写回。
 */

/** 首页设置 + 收藏的车 + 搜索历史一次读出的快照。字段为 null 表示没存过，由仓库套默认值 */
data class TransitLocalSnapshot(
    /** 当前城市（TransitCity 的 JSON）；为 null 表示从没选过城市 */
    val selectedCityJson: String? = null,
    val engineMode: String? = null,
    val aggregatedPriority: String? = null,
    val transitCodeProvider: String? = null,
    val nearbyStopsCount: Int? = null,
    val liveNotificationEnabled: Boolean? = null,
    val favorites: List<FavoriteStationLine> = emptyList(),
    val searchHistory: List<String> = emptyList()
)

/** TransitRepository 的存储：设置（旧 `trackr_prefs` / 新 `trackr_settings`）、收藏的车、搜索历史 */
interface TransitLocalStore {
    suspend fun load(): TransitLocalSnapshot
    suspend fun saveSelectedCityJson(json: String)
    suspend fun saveEngineMode(name: String)
    suspend fun saveAggregatedPriority(name: String)
    suspend fun saveTransitCodeProvider(id: String)
    suspend fun saveNearbyStopsCount(count: Int)
    suspend fun saveLiveNotificationEnabled(enabled: Boolean)
    suspend fun saveFavorites(list: List<FavoriteStationLine>)
    suspend fun saveSearchHistory(list: List<String>)
    suspend fun clearSearchHistory()

    /** 收藏页一次性数据修复的版本号（旧 `favorites_group_migration_version`）；没记过为 0 */
    suspend fun favoritesGroupMigrationVersion(): Int
    suspend fun saveFavoritesGroupMigrationVersion(version: Int)
}

/** RouteGroupRepository 的存储：路线（TransitGroup）和收藏分组 */
interface RouteGroupStore {
    /** 存过的路线，顺序同存入时；从没存过时为 null。旧实现里 JSON 损坏时先备份原文再返回空列表 */
    suspend fun loadGroups(): List<TransitGroup>?

    /** 存过的收藏分组；没存过或损坏时为 null（仓库会写入两个默认分组） */
    suspend fun loadFavoriteGroups(): List<FavoriteGroup>?

    suspend fun saveGroups(list: List<TransitGroup>)
    suspend fun saveFavoriteGroups(list: List<FavoriteGroup>)

    /** 两样一起写（删收藏分组时用）。新实现是一个事务 */
    suspend fun saveGroupsAndFavoriteGroups(groups: List<TransitGroup>, favoriteGroups: List<FavoriteGroup>)
}

/** ScheduledReminderRepository 的存储 */
interface ReminderStore {
    /** 旧实现里 JSON 损坏时先备份原文再返回空列表 */
    suspend fun load(): List<ScheduledReminder>

    /** 整份写回；新实现保留每个提醒已记下的计划触发时刻 */
    suspend fun save(list: List<ScheduledReminder>)
}

/** 每个提醒上次给系统闹钟排的触发时刻（旧 `trackr_reminder_schedule_state`，新 `reminder.lastScheduledTriggerAt`） */
interface ReminderScheduleStateStore {
    suspend fun loadAll(): Map<String, Long>
    suspend fun set(reminderId: String, triggerAtMillis: Long)
    suspend fun remove(reminderId: String)
}

/** 高德 Key 两槽（Android 槽、Web 槽，严格分开、互不回落）。值为 null 表示没填 */
interface AmapKeyStore {
    suspend fun loadAndroidKey(): String?
    suspend fun loadWebKey(): String?
    suspend fun saveAndroidKey(key: String?)
    suspend fun saveWebKey(key: String?)
}

/**
 * 按调用顺序串行执行写入，不阻塞调用方（给非挂起的 setter 用，效果同 SharedPreferences.apply()）。
 * Mutex 是先到先得的，[CoroutineStart.UNDISPATCHED] 保证在调用线程上就去排队，所以执行顺序 = 调用顺序。
 */
class SerialWriter(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun enqueue(block: suspend () -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                // 一次写失败不影响后面的写入，也不让应用崩（旧版 apply() 失败同样是静默的）
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }

    /** 写入失败时的回调（默认什么都不做；Android 上接日志） */
    var onError: (Exception) -> Unit = {}

    /** 等之前排进来的写入都做完 */
    suspend fun flush() {
        mutex.withLock { }
    }
}

/**
 * 收藏页一次性数据修复（`FavoritesGroupViewModel.runDataMigrationIfNeeded`）记的版本号。
 * 第 9 步从 ViewModel 直接读写 SharedPreferences 改成经这里，存在设置里（旧 `trackr_prefs` / 新 `trackr_settings`）。
 */
interface LocalMigrationFlags {
    suspend fun favoritesGroupMigrationVersion(): Int
    suspend fun setFavoritesGroupMigrationVersion(version: Int)
}

class StoreMigrationFlags(private val store: TransitLocalStore) : LocalMigrationFlags {
    override suspend fun favoritesGroupMigrationVersion(): Int = store.favoritesGroupMigrationVersion()

    override suspend fun setFavoritesGroupMigrationVersion(version: Int) =
        store.saveFavoritesGroupMigrationVersion(version)
}
