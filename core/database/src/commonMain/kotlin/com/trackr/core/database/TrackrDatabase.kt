package com.trackr.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.trackr.core.database.dao.FavoriteGroupDao
import com.trackr.core.database.dao.FavoriteLineDao
import com.trackr.core.database.dao.LegacyImportDao
import com.trackr.core.database.dao.LineStopsCacheDao
import com.trackr.core.database.dao.ReminderDao
import com.trackr.core.database.dao.RouteDao
import com.trackr.core.database.dao.SearchHistoryDao
import com.trackr.core.database.dao.StoreMetaDao
import com.trackr.core.database.entity.CandidateLineEntity
import com.trackr.core.database.entity.FavoriteGroupEntity
import com.trackr.core.database.entity.FavoriteLineEntity
import com.trackr.core.database.entity.LineStopsCacheEntity
import com.trackr.core.database.entity.ReminderEntity
import com.trackr.core.database.entity.RouteEntity
import com.trackr.core.database.entity.RouteLegEntity
import com.trackr.core.database.entity.SearchHistoryEntity
import com.trackr.core.database.entity.StoreMetaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * 追客本地数据库（Android：`databases/trackr.db`，参与备份；重构计划 §2.4）。
 *
 * 版本 1。以后改表结构：版本号 +1、写 Migration、用 schemas/ 里导出的 JSON 做迁移测试，**不要**开 destructive fallback。
 */
@Database(
    entities = [
        FavoriteLineEntity::class,
        FavoriteGroupEntity::class,
        RouteEntity::class,
        RouteLegEntity::class,
        CandidateLineEntity::class,
        ReminderEntity::class,
        SearchHistoryEntity::class,
        LineStopsCacheEntity::class,
        StoreMetaEntity::class
    ],
    version = 1,
    exportSchema = true
)
@ConstructedBy(TrackrDatabaseConstructor::class)
abstract class TrackrDatabase : RoomDatabase() {
    abstract fun favoriteLines(): FavoriteLineDao
    abstract fun favoriteGroups(): FavoriteGroupDao
    abstract fun routes(): RouteDao
    abstract fun reminders(): ReminderDao
    abstract fun searchHistory(): SearchHistoryDao
    abstract fun lineStopsCache(): LineStopsCacheDao
    abstract fun storeMeta(): StoreMetaDao
    abstract fun legacyImport(): LegacyImportDao

    companion object {
        /** Android 上的库文件名（`databases/` 下），备份规则按这个名字写 */
        const val FILE_NAME = "trackr.db"
    }
}

/** Room 编译器为每个平台生成 actual */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object TrackrDatabaseConstructor : RoomDatabaseConstructor<TrackrDatabase> {
    override fun initialize(): TrackrDatabase
}

/** 各平台的 builder 统一收尾：查询跑在 IO 线程 */
fun RoomDatabase.Builder<TrackrDatabase>.buildTrackrDatabase(): TrackrDatabase =
    setQueryCoroutineContext(Dispatchers.IO).build()
