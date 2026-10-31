package com.trackr.core.data.legacy

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.trackr.core.datastore.AmapKeyKeys
import com.trackr.core.datastore.SettingsKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 设置和高德 Key 的只读 DataMigration（不用会删旧 key 的 SharedPreferencesMigration） */
@RunWith(RobolectricTestRunner::class)
class SettingsMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        LegacyFixtures.clearAll(context)
    }

    @Test
    fun settings_copiedWithTypes_andLegacyKept() = runBlocking {
        LegacyFixtures.writeAll(context)
        val before = LegacyFixtures.snapshot(context)
        val migration = LegacySettingsMigration(context, generation = 0)

        assertTrue(migration.shouldMigrate(emptyPreferences()))
        val result = migration.migrate(emptyPreferences())
        migration.cleanUp()

        assertEquals("MYGOLBS", result[SettingsKeys.ENGINE_MODE])
        assertEquals(6, result[SettingsKeys.NEARBY_STOPS_COUNT])
        assertEquals(false, result[SettingsKeys.LIVE_NOTIFICATION_ENABLED])
        assertEquals(2, result[SettingsKeys.FAVORITES_GROUP_MIGRATION_VERSION])
        assertEquals(0, result[SettingsKeys.LEGACY_IMPORT_GEN])
        // 7 个设置 + 导入代数；收藏、历史这些列表进 Room，不进设置文件
        assertEquals(8, result.asMap().size)
        assertEquals(before, LegacyFixtures.snapshot(context))
    }

    @Test
    fun settings_sameGeneration_notMigratedAgain() = runBlocking {
        val migration = LegacySettingsMigration(context, generation = 2)
        assertFalse(migration.shouldMigrate(mutablePreferencesOf(SettingsKeys.LEGACY_IMPORT_GEN to 2)))
        assertTrue(migration.shouldMigrate(mutablePreferencesOf(SettingsKeys.LEGACY_IMPORT_GEN to 1)))
    }

    @Test
    fun settings_reimport_overwritesOnlyKeysPresentInLegacy() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit().putString("engine_mode", "CHELAILE").commit()
        val current = mutablePreferencesOf(
            SettingsKeys.ENGINE_MODE to "MYGOLBS",
            SettingsKeys.NEARBY_STOPS_COUNT to 8,
            SettingsKeys.LEGACY_IMPORT_GEN to 0
        )
        val result = LegacySettingsMigration(context, generation = 1).migrate(current)
        assertEquals("CHELAILE", result[SettingsKeys.ENGINE_MODE])
        assertEquals(8, result[SettingsKeys.NEARBY_STOPS_COUNT])
        assertEquals(1, result[SettingsKeys.LEGACY_IMPORT_GEN])
    }

    @Test
    fun settings_wrongType_isSkippedWithoutCrash() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.TRANSIT).edit()
            .putInt("engine_mode", 3)
            .putString("nearby_stops_count", "6")
            .putBoolean("selected_city_json", true)
            .commit()
        val result = LegacySettingsMigration(context, generation = 0).migrate(emptyPreferences())
        assertNull(result[SettingsKeys.ENGINE_MODE])
        assertNull(result[SettingsKeys.NEARBY_STOPS_COUNT])
        assertNull(result[SettingsKeys.SELECTED_CITY_JSON])
    }

    @Test
    fun amapKeys_blankLegacyValue_doesNotClearNewKey() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).edit()
            .putString("custom_amap_android_key", "  ")
            .putBoolean("migrated_from_trackr_prefs", true)
            .commit()
        val current = mutablePreferencesOf(AmapKeyKeys.ANDROID to "typed-in-new-store", AmapKeyKeys.LEGACY_IMPORT_GEN to 0)
        val result = LegacyAmapKeyMigration(context, generation = 1).migrate(current)
        assertEquals("typed-in-new-store", result[AmapKeyKeys.ANDROID])
        assertEquals(1, result[AmapKeyKeys.LEGACY_IMPORT_GEN])
    }

    @Test
    fun amapKeys_neverWrittenToSettingsMigration() = runBlocking {
        LegacyFixtures.writeAll(context)
        val result = LegacySettingsMigration(context, generation = 0).migrate(emptyPreferences())
        assertNull(result[AmapKeyKeys.ANDROID])
        assertNull(result[AmapKeyKeys.WEB])
    }

    @Test
    fun amapKeys_trimmed_andLegacyKept() = runBlocking {
        LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).edit()
            .putString("custom_amap_web_key", " web ")
            .commit()
        val result = LegacyAmapKeyMigration(context, generation = 0).migrate(emptyPreferences())
        assertEquals("web", result[AmapKeyKeys.WEB])
        assertEquals(" web ", LegacyFixtures.prefs(context, LegacyPrefs.AMAP_KEYS).getString("custom_amap_web_key", null))
    }
}
