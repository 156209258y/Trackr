package com.trackr.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.trackr.core.database.entity.CandidateLineEntity
import com.trackr.core.database.entity.FavoriteGroupEntity
import com.trackr.core.database.entity.FavoriteLineEntity
import com.trackr.core.database.entity.LineStopsCacheEntity
import com.trackr.core.database.entity.ReminderEntity
import com.trackr.core.database.entity.RouteEntity
import com.trackr.core.database.entity.RouteLegEntity
import com.trackr.core.database.entity.SearchHistoryEntity
import com.trackr.core.database.entity.StoreMetaEntity
import kotlinx.coroutines.flow.Flow

/*
 * 第 9 步的写法：仓库在内存里算好整份列表，DAO 在一个事务里整份替换（和旧版每次整份写 JSON 的语义一样，最稳）。
 * 按行改（拖动排序、改分组）第 14 步再加。
 */

@Dao
interface FavoriteLineDao {
    @Query("SELECT * FROM favorite_line ORDER BY position")
    suspend fun getAll(): List<FavoriteLineEntity>

    @Query("SELECT * FROM favorite_line ORDER BY position")
    fun observeAll(): Flow<List<FavoriteLineEntity>>

    @Query("DELETE FROM favorite_line")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FavoriteLineEntity>)

    @Transaction
    suspend fun replaceAll(items: List<FavoriteLineEntity>) {
        deleteAll()
        insertAll(items)
    }
}

@Dao
interface FavoriteGroupDao {
    @Query("SELECT * FROM favorite_group ORDER BY position")
    suspend fun getAll(): List<FavoriteGroupEntity>

    @Query("SELECT COUNT(*) FROM favorite_group")
    suspend fun count(): Int

    @Query("DELETE FROM favorite_group")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FavoriteGroupEntity>)

    @Transaction
    suspend fun replaceAll(items: List<FavoriteGroupEntity>) {
        deleteAll()
        insertAll(items)
    }
}

/** 一条路线连同它的程和候选线 */
data class RouteRows(
    val route: RouteEntity,
    val legs: List<RouteLegEntity>,
    val candidates: List<CandidateLineEntity>
)

@Dao
interface RouteDao {
    @Query("SELECT * FROM route ORDER BY position")
    suspend fun getRoutes(): List<RouteEntity>

    @Query("SELECT * FROM route_leg ORDER BY routeId, position")
    suspend fun getLegs(): List<RouteLegEntity>

    @Query("SELECT * FROM route_candidate_line ORDER BY routeId, legPosition, position")
    suspend fun getCandidates(): List<CandidateLineEntity>

    @Query("SELECT COUNT(*) FROM route")
    suspend fun count(): Int

    @Query("DELETE FROM route_candidate_line")
    suspend fun deleteAllCandidates()

    @Query("DELETE FROM route_leg")
    suspend fun deleteAllLegs()

    @Query("DELETE FROM route")
    suspend fun deleteAllRoutes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(items: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegs(items: List<RouteLegEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandidates(items: List<CandidateLineEntity>)

    /** 全部路线（按 position），每条带上自己的程和候选线 */
    @Transaction
    suspend fun getAll(): List<RouteRows> {
        val legsByRoute = getLegs().groupBy { it.routeId }
        val candidatesByRoute = getCandidates().groupBy { it.routeId }
        return getRoutes().map { route ->
            RouteRows(
                route = route,
                legs = legsByRoute[route.id].orEmpty(),
                candidates = candidatesByRoute[route.id].orEmpty()
            )
        }
    }

    /** 整份替换。不靠外键级联，三张表都显式清空，结果和级联开没开无关 */
    @Transaction
    suspend fun replaceAll(rows: List<RouteRows>) {
        deleteAllCandidates()
        deleteAllLegs()
        deleteAllRoutes()
        insertRoutes(rows.map { it.route })
        insertLegs(rows.flatMap { it.legs })
        insertCandidates(rows.flatMap { it.candidates })
    }

    @Query("DELETE FROM favorite_group")
    suspend fun deleteAllFavoriteGroups()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteGroups(items: List<FavoriteGroupEntity>)

    /** 路线和收藏分组一起换（删分组时同时把它的路线改成不分组），一个事务 */
    @Transaction
    suspend fun replaceRoutesAndGroups(rows: List<RouteRows>, groups: List<FavoriteGroupEntity>) {
        replaceAll(rows)
        deleteAllFavoriteGroups()
        insertFavoriteGroups(groups)
    }
}

/** 提醒的计划触发时刻 */
data class ReminderTrigger(
    val id: String,
    val lastScheduledTriggerAt: Long?
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY position")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT id, lastScheduledTriggerAt FROM reminder")
    suspend fun getTriggers(): List<ReminderTrigger>

    @Query("SELECT lastScheduledTriggerAt FROM reminder WHERE id = :id")
    suspend fun getLastScheduledTriggerAt(id: String): Long?

    @Query("UPDATE reminder SET lastScheduledTriggerAt = :at WHERE id = :id")
    suspend fun setLastScheduledTriggerAt(id: String, at: Long?)

    @Query("DELETE FROM reminder")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReminderEntity>)

    /**
     * 整份替换提醒列表，但保留每个提醒已记下的计划触发时刻：
     * 传进来的行里 `lastScheduledTriggerAt` 一律忽略，按 id 用库里现有的值（新提醒为 null）。
     * 读旧值和写新值在同一个事务里，期间别的写入插不进来。
     */
    @Transaction
    suspend fun replaceAllKeepingTriggers(items: List<ReminderEntity>) {
        val triggers = getTriggers().associate { it.id to it.lastScheduledTriggerAt }
        deleteAll()
        insertAll(items.map { it.copy(lastScheduledTriggerAt = triggers[it.id]) })
    }

    /** 迁移用：整份替换，计划触发时刻用传进来的值 */
    @Transaction
    suspend fun replaceAll(items: List<ReminderEntity>) {
        deleteAll()
        insertAll(items)
    }
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY position")
    suspend fun getAll(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SearchHistoryEntity>)

    @Transaction
    suspend fun replaceAll(items: List<SearchHistoryEntity>) {
        deleteAll()
        insertAll(items)
    }
}

@Dao
interface LineStopsCacheDao {
    @Query("SELECT * FROM line_stops_cache WHERE cacheKey = :key")
    suspend fun get(key: String): LineStopsCacheEntity?

    @Upsert
    suspend fun upsert(entry: LineStopsCacheEntity)

    @Query("DELETE FROM line_stops_cache WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

@Dao
interface StoreMetaDao {
    @Query("SELECT value FROM store_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: StoreMetaEntity)
}
