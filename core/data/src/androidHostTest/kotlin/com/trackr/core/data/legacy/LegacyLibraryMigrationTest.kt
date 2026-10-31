package com.trackr.core.data.legacy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.trackr.core.data.local.RoomDataStoreTransitStore
import com.trackr.core.data.local.RoomReminderScheduleStateStore
import com.trackr.core.data.local.RoomReminderStore
import com.trackr.core.data.local.RoomRouteGroupStore
import com.trackr.core.data.local.toEntity
import com.trackr.core.database.TrackrDatabase
import com.trackr.core.database.createInMemoryTrackrDatabase
import com.trackr.core.datastore.AmapKeyKeys
import com.trackr.core.datastore.SettingsKeys
import com.trackr.core.datastore.TrackrDataStoreFiles
import com.trackr.core.datastore.createPreferencesDataStore
import com.trackr.core.datastore.trackrDataStorePath
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * 旧 SharedPreferences -> Room / DataStore（重构计划第 9 步、风险 R1）。
 * 每个旧 key 都有一条「读旧 JSON」用例；损坏的 JSON 不崩、原文原样备份；旧文件迁移前后一字不差。
 */
@RunWith(RobolectricTestRunner::class)
class LegacyLibraryMigrationTest {

    private lateinit var context: Context
    private lateinit var db: TrackrDatabase
    private var scope: CoroutineScope? = null
    private lateinit var settings: DataStore<Preferences>
    private lateinit var keys: DataStore<Preferences>

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LegacyFixtures.clearAll(context)
        File(context.filesDir, LegacyPrefs.BACKUP_DIR).deleteRecursively()
        File(context.filesDir, TrackrDataStoreFiles.DIRECTORY).deleteRecursively()
        db = createInMemoryTrackrDatabase(context)
    }

    @After
    fun tearDown() = runBlocking {
        closeStores()
        db.close()
    }

    private suspend fun closeStores() {
        scope?.coroutineContext?.get(Job)?.cancelAndJoin()
        scope = null
    }

    /** 和 StorageBackend 一样建两个 DataStore（带只读的 DataMigration） */
    private suspend fun migrate(generation: Int = 0): LegacyLibraryMigration.Report {
        closeStores()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        settings = createPreferencesDataStore(
            context.trackrDataStorePath(TrackrDataStoreFiles.SETTINGS),
            newScope,
            listOf(LegacySettingsMigration(context, generation))
        )
        keys = createPreferencesDataStore(
            context.trackrDataStorePath(TrackrDataStoreFiles.AMAP_KEYS),
            newScope,
            listOf(LegacyAmapKeyMigration(context, generation))
        )
        return LegacyLibraryMigration(context, db, settings, keys, generation).runIfNeeded()
    }

    private suspend fun settingsNow() = settings.data.first()
    private suspend fun keysNow() = keys.data.first()

    private fun backupFile(name: String) = File(File(context.filesDir, LegacyPrefs.BACKUP_DIR), name)

    // ---- 每个旧 key 一条 ----

    @Test
    fun favoritesJson_importedInOrderFieldByField() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val expected = json.decodeFromString<List<FavoriteStationLine>>(LegacyFixtures.FAVORITES_JSON)
        assertEquals(expected, RoomDataStoreTransitStore(db, settings).load().favorites)
        assertEquals(listOf("051_100_7", "mg_200_3"), db.favoriteLines().getAll().map { it.id })
    }

    @Test
    fun searchHistoryJson_importedInOrder() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        assertEquals(listOf("59", "7路", "万达"), RoomDataStoreTransitStore(db, settings).load().searchHistory)
    }

    @Test
    fun groupsListJson_importedWithLegsCandidatesAndPaths() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val expected = json.decodeFromString<List<TransitGroup>>(LegacyFixtures.GROUPS_JSON)
        val loaded = RoomRouteGroupStore(db).loadGroups()
        assertEquals(expected, loaded)
        // 指向不存在分组的路线也照样留着（旧版也这样显示）
        assertEquals("deleted-group", loaded!!.single { it.id == "route-3" }.customGroupId)
        assertEquals(2, loaded.single { it.id == "route-2" }.legs[0].ridePath.size)
    }

    @Test
    fun favoriteGroupsListJson_imported() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val expected = json.decodeFromString<List<FavoriteGroup>>(LegacyFixtures.FAVORITE_GROUPS_JSON)
        assertEquals(expected, RoomRouteGroupStore(db).loadFavoriteGroups())
    }

    @Test
    fun remindersListJson_importedWithScheduleState() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val expected = json.decodeFromString<List<ScheduledReminder>>(LegacyFixtures.REMINDERS_JSON)
        assertEquals(expected, RoomReminderStore(db).load())
        // next_trigger_<id> -> reminder.lastScheduledTriggerAt；没有对应提醒的记录丢掉
        assertEquals(mapOf("rem-1" to 1_800_000_000_000L), RoomReminderScheduleStateStore(db).loadAll())
    }

    @Test
    fun settingsKeys_imported() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val prefs = settingsNow()
        assertEquals(LegacyFixtures.CITY_JSON, prefs[SettingsKeys.SELECTED_CITY_JSON])
        assertEquals("MYGOLBS", prefs[SettingsKeys.ENGINE_MODE])
        // D1：旧的聚合优先级不再导入
        assertFalse(prefs.asMap().keys.any { it.name == "aggregated_source_priority" })
        assertEquals("alipay", prefs[SettingsKeys.PREFERRED_TRANSIT_CODE_PROVIDER])
        assertEquals(6, prefs[SettingsKeys.NEARBY_STOPS_COUNT])
        assertEquals(false, prefs[SettingsKeys.LIVE_NOTIFICATION_ENABLED])
        assertEquals(2, prefs[SettingsKeys.FAVORITES_GROUP_MIGRATION_VERSION])
        assertEquals(1, prefs[SettingsKeys.LEGACY_MIGRATED])

        val snapshot = RoomDataStoreTransitStore(db, settings).load()
        assertEquals(LegacyFixtures.CITY_JSON, snapshot.selectedCityJson)
        assertEquals(6, snapshot.nearbyStopsCount)
    }

    @Test
    fun amapKeys_fromKeyFile() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        assertEquals("android-key-123", keysNow()[AmapKeyKeys.ANDROID])
        assertEquals("web-key-456", keysNow()[AmapKeyKeys.WEB])
        // Key 不进设置文件（设置文件参与备份，R22）
        assertNull(settingsNow()[AmapKeyKeys.ANDROID])
        assertNull(settingsNow()[AmapKeyKeys.WEB])
    }

    @Test
    fun amapKeys_pre0a4fdab_readFromTrackrPrefs_andLeftInPlace() = runBlocking {
        // 0a4fdab 之前：Key 在 trackr_prefs，trackr_amap_keys 还不存在
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit()
            .putString("custom_amap_android_key", " old-android ")
            .putString("custom_amap_web_key", "old-web")
            .commit()
        migrate()
        assertEquals("old-android", keysNow()[AmapKeyKeys.ANDROID])
        assertEquals("old-web", keysNow()[AmapKeyKeys.WEB])
        // 旧值不删：回退旧版本还能读到
        assertEquals(" old-android ", LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).getString("custom_amap_android_key", null))
    }

    @Test
    fun amapKeys_keyFileWinsOverTrackrPrefs() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).edit()
            .putString("custom_amap_android_key", "new-android")
            .commit()
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit()
            .putString("custom_amap_android_key", "stale-android")
            .putString("custom_amap_web_key", "only-in-trackr-prefs")
            .commit()
        migrate()
        assertEquals("new-android", keysNow()[AmapKeyKeys.ANDROID])
        // 这个槽在 Key 文件里是空的，又没有搬迁标记，用 trackr_prefs 的
        assertEquals("only-in-trackr-prefs", keysNow()[AmapKeyKeys.WEB])
    }

    @Test
    fun amapKeys_afterMigratedFlag_trackrPrefsIgnored() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).edit()
            .putBoolean("migrated_from_trackr_prefs", true)
            .commit()
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit()
            .putString("custom_amap_android_key", "stale-android")
            .commit()
        migrate()
        assertNull(keysNow()[AmapKeyKeys.ANDROID])
    }

    // ---- 旧数据保留 ----

    @Test
    fun legacyFiles_unchangedAfterMigration() = runBlocking {
        LegacyFixtures.writeAll(context)
        val before = LegacyFixtures.snapshot(context)
        val report = migrate()
        assertEquals(before, LegacyFixtures.snapshot(context))
        assertTrue(report.ran)
        assertTrue(report.corruptKeys.isEmpty())
        assertFalse(File(context.filesDir, LegacyPrefs.BACKUP_DIR).exists())
        assertEquals(
            mapOf("favorites" to 2, "searchHistory" to 3, "routes" to 3, "favoriteGroups" to 3, "reminders" to 2),
            report.imported
        )
    }

    @Test
    fun freshInstall_noLegacyData_importsNothing() = runBlocking {
        val report = migrate()
        assertTrue(report.ran)
        assertTrue(report.imported.isEmpty())
        assertTrue(db.favoriteLines().getAll().isEmpty())
        assertNull(RoomRouteGroupStore(db).loadFavoriteGroups())
        assertNull(settingsNow()[SettingsKeys.SELECTED_CITY_JSON])
        assertEquals(1, settingsNow()[SettingsKeys.LEGACY_MIGRATED])
    }

    @Test
    fun blankJson_isEmptyNotCorrupt() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.REMINDERS).edit().putString("reminders_list_json", "").commit()
        val report = migrate()
        assertTrue(report.corruptKeys.isEmpty())
        assertEquals(0, report.imported["reminders"])
    }

    // ---- 损坏的 JSON ----

    @Test
    fun corruptJson_doesNotCrash_skipsOnlyThatItem_andBacksUpRaw() = runBlocking {
        LegacyFixtures.writeAll(context)
        val corrupt = "[{\"id\":\"broken\",\"cityId\":"
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit().putString("favorites_json", corrupt).commit()
        LegacyFixtures.prefs(context, LegacyPrefs.REMINDERS).edit().putString("reminders_list_json", "not json").commit()

        val report = migrate()

        assertEquals(listOf("trackr_prefs/favorites_json", "trackr_scheduled_reminders/reminders_list_json"), report.corruptKeys)
        // 坏的那项没导，别的照常
        assertTrue(db.favoriteLines().getAll().isEmpty())
        assertTrue(db.reminders().getAll().isEmpty())
        assertEquals(3, RoomRouteGroupStore(db).loadGroups()!!.size)
        assertEquals(listOf("59", "7路", "万达"), RoomDataStoreTransitStore(db, settings).load().searchHistory)
        // 原文原样复制一份，旧 key 也还在
        assertEquals(corrupt, backupFile("trackr_prefs_favorites_json.json").readText())
        assertEquals("not json", backupFile("trackr_scheduled_reminders_reminders_list_json.json").readText())
        assertEquals(corrupt, LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).getString("favorites_json", null))
        assertEquals(
            setOf("trackr_prefs/favorites_json", "trackr_scheduled_reminders/reminders_list_json"),
            settingsNow()[SettingsKeys.LEGACY_CORRUPT_KEYS]
        )
        assertEquals(1, settingsNow()[SettingsKeys.LEGACY_MIGRATED])
    }

    @Test
    fun existingCorruptBackups_copiedVerbatim_andKept() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.ROUTE_GROUPS).edit()
            .putString("groups_list_json_corrupt_backup_1793000000000", "[{\"id\":\"half")
            .putString("favorite_groups_list_json_corrupt_backup_1793000000001", "broken-favs")
            .commit()
        LegacyFixtures.prefs(context, LegacyPrefs.REMINDERS).edit()
            .putString("reminders_list_json_corrupt_backup_1793000000002", "{oops")
            .commit()

        val report = migrate()

        assertEquals("[{\"id\":\"half", backupFile("trackr_route_groups_groups_list_json_corrupt_backup_1793000000000.json").readText())
        assertEquals("broken-favs", backupFile("trackr_route_groups_favorite_groups_list_json_corrupt_backup_1793000000001.json").readText())
        assertEquals("{oops", backupFile("trackr_scheduled_reminders_reminders_list_json_corrupt_backup_1793000000002.json").readText())
        assertEquals(3, report.backupFiles.size)
        assertEquals(
            "broken-favs",
            LegacyFixtures.prefs(context, LegacyPrefs.ROUTE_GROUPS).getString("favorite_groups_list_json_corrupt_backup_1793000000001", null)
        )
    }

    @Test
    fun backupFiles_neverOverwritten_whenRawChanges() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit().putString("favorites_json", "bad-1").commit()
        migrate(generation = 0)
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit().putString("favorites_json", "bad-2").commit()
        migrate(generation = 1)
        assertEquals("bad-1", backupFile("trackr_prefs_favorites_json.json").readText())
        assertEquals("bad-2", backupFile("trackr_prefs_favorites_json_2.json").readText())
    }

    // ---- 只跑一次 / 来回切开关 ----

    @Test
    fun sameGeneration_runsOnlyOnce() = runBlocking {
        LegacyFixtures.writeAll(context)
        assertTrue(migrate().ran)
        // 新存储里改了收藏；同一代再启动不会被旧数据冲掉
        db.favoriteLines().replaceAll(emptyList())
        val second = migrate()
        assertFalse(second.ran)
        assertTrue(db.favoriteLines().getAll().isEmpty())
    }

    @Test
    fun newGeneration_reimports_onlyKeysPresentInLegacy() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate(generation = 0)

        // 中间用旧存储跑了一次：收藏改了、提醒文件被清空（key 不在了）、城市改了
        val newFavs = json.decodeFromString<List<FavoriteStationLine>>(LegacyFixtures.FAVORITES_JSON).take(1)
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit()
            .putString("favorites_json", json.encodeToString(newFavs))
            .putString("selected_city_json", "{\"cityId\":\"034\",\"cityName\":\"上海\"}")
            .commit()
        LegacyFixtures.prefs(context, LegacyPrefs.REMINDERS).edit().clear().commit()
        // 新存储里还有一个旧文件里没有的 Key 槽，不能被清掉
        LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).edit().remove("custom_amap_web_key").commit()

        val report = migrate(generation = 1)

        assertTrue(report.ran)
        assertEquals(newFavs, RoomDataStoreTransitStore(db, settings).load().favorites)
        assertEquals("{\"cityId\":\"034\",\"cityName\":\"上海\"}", settingsNow()[SettingsKeys.SELECTED_CITY_JSON])
        // 旧文件里没有提醒 key：库里的提醒保持不变
        assertEquals(2, db.reminders().getAll().size)
        assertEquals("web-key-456", keysNow()[AmapKeyKeys.WEB])
        assertEquals("android-key-123", keysNow()[AmapKeyKeys.ANDROID])
    }

    @Test
    fun importIsOneTransaction_metaWrittenWithData() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate(generation = 3)
        assertEquals("3", db.storeMeta().get(LegacyLibraryMigration.META_LEGACY_IMPORT_GEN))
        assertFalse(migrate(generation = 3).ran)
        // 库重建（例如库文件损坏或丢失）后标记跟着没了，下次会重新导入
        db.close()
        db = createInMemoryTrackrDatabase(context)
        assertTrue(migrate(generation = 3).ran)
        assertEquals(2, db.favoriteLines().getAll().size)
    }

    @Test
    fun entityMapping_ignoresIncomingTriggerOnNormalSave() = runBlocking {
        LegacyFixtures.writeAll(context)
        migrate()
        val reminders = RoomReminderStore(db).load()
        // 正常保存时不会把迁移进来的计划触发时刻冲掉
        RoomReminderStore(db).save(reminders.reversed())
        assertEquals(1_800_000_000_000L, db.reminders().getLastScheduledTriggerAt("rem-1"))
        assertEquals(reminders.reversed().map { it.id }, db.reminders().getAll().map { it.id })
        assertEquals(reminders[0].toEntity(1).copy(lastScheduledTriggerAt = 1_800_000_000_000L), db.reminders().getAll()[1])
    }
}
