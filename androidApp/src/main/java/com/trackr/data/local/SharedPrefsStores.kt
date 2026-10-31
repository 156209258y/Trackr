package com.trackr.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.trackr.core.data.legacy.LegacyDataSource
import com.trackr.core.data.local.AmapKeyStore
import com.trackr.core.data.local.ReminderStore
import com.trackr.core.data.local.RouteGroupStore
import com.trackr.core.data.local.TransitLocalSnapshot
import com.trackr.core.data.local.TransitLocalStore
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitGroup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.trackr.data.remote.amap.AmapKeyStore as LegacyAmapKeyStore

/*
 * 旧存储：原来的 SharedPreferences + JSON（USE_NEW_STORE 关掉时用，风险 R1）。
 *
 * 这些类是第 9 步之前三个仓库里读写 SharedPreferences 的代码原样搬出来的：文件名、key、JSON 格式、
 * 损坏时的备份（148d67c）、apply() 的写法都没变。旧存储至少保留一个发版周期，之后整体删掉。
 */

private val legacyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** `trackr_prefs`：设置、收藏的车、搜索历史、收藏页数据修复版本号 */
class SharedPrefsTransitStore(private val prefs: SharedPreferences) : TransitLocalStore {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override suspend fun load(): TransitLocalSnapshot = TransitLocalSnapshot(
        // 旧版 hasSavedCity = prefs.contains(...)；存的是空串时也算存过
        selectedCityJson = if (prefs.contains(KEY_SELECTED_CITY)) prefs.getString(KEY_SELECTED_CITY, null).orEmpty() else null,
        // D1：旧的聚合配置按和新存储导入同样的规则落成主数据源（只读，不改旧文件）
        engineMode = LegacyDataSource.resolve(
            engineMode = prefs.getString(KEY_ENGINE_MODE, null),
            aggregatedPriority = prefs.getString(KEY_AGGREGATED_PRIORITY, null)
        ),
        transitCodeProvider = prefs.getString(KEY_TRANSIT_CODE_PROVIDER, null),
        nearbyStopsCount = if (prefs.contains(KEY_NEARBY_STOPS_COUNT)) prefs.getInt(KEY_NEARBY_STOPS_COUNT, 4) else null,
        liveNotificationEnabled = if (prefs.contains(KEY_LIVE_NOTIFICATION)) prefs.getBoolean(KEY_LIVE_NOTIFICATION, true) else null,
        showOnLockScreen = if (prefs.contains(KEY_SHOW_ON_LOCK_SCREEN)) prefs.getBoolean(KEY_SHOW_ON_LOCK_SCREEN, true) else null,
        favorites = decodeOrEmpty(prefs.getString(KEY_FAVORITES, null)),
        searchHistory = decodeOrEmpty(prefs.getString(KEY_SEARCH_HISTORY, null))
    )

    private inline fun <reified T> decodeOrEmpty(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            legacyJson.decodeFromString<List<T>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun saveSelectedCityJson(json: String) {
        prefs.edit().putString(KEY_SELECTED_CITY, json).apply()
    }

    override suspend fun saveEngineMode(name: String) {
        prefs.edit().putString(KEY_ENGINE_MODE, name).apply()
    }

    override suspend fun saveTransitCodeProvider(id: String) {
        prefs.edit().putString(KEY_TRANSIT_CODE_PROVIDER, id).apply()
    }

    override suspend fun saveNearbyStopsCount(count: Int) {
        prefs.edit().putInt(KEY_NEARBY_STOPS_COUNT, count).apply()
    }

    override suspend fun saveLiveNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_NOTIFICATION, enabled).apply()
    }

    override suspend fun saveShowOnLockScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ON_LOCK_SCREEN, enabled).apply()
    }

    override suspend fun saveFavorites(list: List<FavoriteStationLine>) {
        prefs.edit().putString(KEY_FAVORITES, legacyJson.encodeToString(list)).apply()
    }

    override suspend fun saveSearchHistory(list: List<String>) {
        prefs.edit().putString(KEY_SEARCH_HISTORY, legacyJson.encodeToString(list)).apply()
    }

    override suspend fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    override suspend fun favoritesGroupMigrationVersion(): Int = prefs.getInt(KEY_FAVORITES_GROUP_MIGRATION_VERSION, 0)

    override suspend fun saveFavoritesGroupMigrationVersion(version: Int) {
        prefs.edit().putInt(KEY_FAVORITES_GROUP_MIGRATION_VERSION, version).apply()
    }

    companion object {
        const val PREFS_NAME = "trackr_prefs"
        private const val KEY_SELECTED_CITY = "selected_city_json"
        private const val KEY_ENGINE_MODE = "engine_mode"
        private const val KEY_AGGREGATED_PRIORITY = "aggregated_source_priority"
        private const val KEY_TRANSIT_CODE_PROVIDER = "preferred_transit_code_provider"
        private const val KEY_NEARBY_STOPS_COUNT = "nearby_stops_count"
        private const val KEY_LIVE_NOTIFICATION = "is_live_notification_enabled"
        private const val KEY_SHOW_ON_LOCK_SCREEN = "show_on_lock_screen"
        private const val KEY_FAVORITES = "favorites_json"
        private const val KEY_SEARCH_HISTORY = "search_history_json"
        const val KEY_FAVORITES_GROUP_MIGRATION_VERSION = "favorites_group_migration_version"
    }
}

/** `trackr_route_groups`：路线和收藏分组；JSON 损坏时先把原文存到 `<key>_corrupt_backup_<时间戳>`（只留最新一份） */
class SharedPrefsRouteGroupStore(private val prefs: SharedPreferences) : RouteGroupStore {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override suspend fun loadGroups(): List<TransitGroup>? {
        val rawJson = prefs.getString(KEY_GROUPS_JSON, null)
        if (rawJson.isNullOrBlank()) return null
        return try {
            legacyJson.decodeFromString<List<TransitGroup>>(rawJson)
        } catch (e: Exception) {
            // 解析失败时先备份原始数据，避免下次写入无声覆盖
            Log.e(TAG, "Failed to parse groups json, backing up raw data", e)
            backupCorruptJson(KEY_GROUPS_JSON, rawJson)
            emptyList()
        }
    }

    override suspend fun loadFavoriteGroups(): List<FavoriteGroup>? {
        val rawJson = prefs.getString(KEY_FAVORITE_GROUPS_JSON, null)
        if (rawJson.isNullOrBlank()) return null
        return try {
            legacyJson.decodeFromString<List<FavoriteGroup>>(rawJson)
        } catch (e: Exception) {
            // 解析失败时先备份原始数据，再由仓库写入默认分组
            Log.e(TAG, "Failed to parse favorite groups json, backing up raw data", e)
            backupCorruptJson(KEY_FAVORITE_GROUPS_JSON, rawJson)
            null
        }
    }

    override suspend fun saveGroups(list: List<TransitGroup>) {
        try {
            prefs.edit().putString(KEY_GROUPS_JSON, legacyJson.encodeToString(list)).apply()
        } catch (_: Exception) {
        }
    }

    override suspend fun saveFavoriteGroups(list: List<FavoriteGroup>) {
        try {
            prefs.edit().putString(KEY_FAVORITE_GROUPS_JSON, legacyJson.encodeToString(list)).apply()
        } catch (_: Exception) {
        }
    }

    override suspend fun saveGroupsAndFavoriteGroups(groups: List<TransitGroup>, favoriteGroups: List<FavoriteGroup>) {
        // 旧版就是先写收藏分组、再写路线，两次 apply
        saveFavoriteGroups(favoriteGroups)
        saveGroups(groups)
    }

    /** 将无法解析的原始 JSON 备份到 `<key>_corrupt_backup_<时间戳>`，同一 key 只保留最近一份 */
    private fun backupCorruptJson(key: String, rawJson: String) {
        val backupPrefix = key + CORRUPT_BACKUP_SUFFIX
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(backupPrefix) }
            .forEach { editor.remove(it) }
        editor.putString(backupPrefix + System.currentTimeMillis(), rawJson)
        editor.apply()
    }

    companion object {
        private const val TAG = "RouteGroupRepository"
        const val PREFS_NAME = "trackr_route_groups"
        const val CORRUPT_BACKUP_SUFFIX = "_corrupt_backup_"
        private const val KEY_GROUPS_JSON = "groups_list_json"
        private const val KEY_FAVORITE_GROUPS_JSON = "favorite_groups_list_json"
    }
}

/** `trackr_scheduled_reminders`：定时提醒；JSON 损坏时先把原文存到 `reminders_list_json_corrupt_backup_<时间戳>` */
class SharedPrefsReminderStore(private val prefs: SharedPreferences) : ReminderStore {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override suspend fun load(): List<ScheduledReminder> {
        val rawJson = prefs.getString(KEY_REMINDERS_JSON, null) ?: return emptyList()
        return try {
            legacyJson.decodeFromString<List<ScheduledReminder>>(rawJson)
        } catch (e: Exception) {
            // 解析失败时先备份原始数据，避免下次写入无声覆盖
            Log.e(TAG, "Failed to parse reminders json, backing up raw data", e)
            backupCorruptJson(rawJson)
            emptyList()
        }
    }

    override suspend fun save(list: List<ScheduledReminder>) {
        try {
            prefs.edit().putString(KEY_REMINDERS_JSON, legacyJson.encodeToString(list)).apply()
        } catch (_: Exception) {
        }
    }

    private fun backupCorruptJson(rawJson: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_CORRUPT_BACKUP_PREFIX) }
            .forEach { editor.remove(it) }
        editor.putString(KEY_CORRUPT_BACKUP_PREFIX + System.currentTimeMillis(), rawJson)
        editor.apply()
    }

    companion object {
        private const val TAG = "ScheduledReminderRepo"
        const val PREFS_NAME = "trackr_scheduled_reminders"
        private const val KEY_REMINDERS_JSON = "reminders_list_json"
        const val KEY_CORRUPT_BACKUP_PREFIX = "reminders_list_json_corrupt_backup_"
    }
}

/**
 * 高德 Key 的旧存储：`trackr_amap_keys`（SharedPreferences）。读写走原来的 `AmapKeyStore` 函数，
 * 打开时照旧把 0a4fdab 之前存在 `trackr_prefs` 里的 Key 搬过来（[LegacyAmapKeyStore.prefs]）。
 */
class SharedPrefsAmapKeyStore(private val prefs: SharedPreferences?) : AmapKeyStore {

    constructor(context: Context) : this(LegacyAmapKeyStore.prefs(context))

    override suspend fun loadAndroidKey(): String? = LegacyAmapKeyStore.readAndroidKey(prefs)

    override suspend fun loadWebKey(): String? = LegacyAmapKeyStore.readWebKey(prefs)

    override suspend fun saveAndroidKey(key: String?) = LegacyAmapKeyStore.writeAndroidKey(prefs, key)

    override suspend fun saveWebKey(key: String?) = LegacyAmapKeyStore.writeWebKey(prefs, key)
}
