package com.trackr.core.data.legacy

import com.trackr.core.model.EngineMode

/**
 * D1：数据源二选一。旧版有「双引擎聚合」模式（`engine_mode = ALL`，ded8132 之前也是默认值）和聚合优先级
 * （`aggregated_source_priority`）。读旧配置时把它们落成一个主数据源：
 *
 * - `engine_mode` 是车来了 / 掌上公交：原样用，旧优先级不管；
 * - `engine_mode` 是 `ALL` 或没存（当年默认就是聚合）：优先级是掌上公交优先就用掌上公交；
 *   否则 `ALL` 用车来了，没存的保持没存（仓库套默认值车来了）；
 * - 其他值（认不出来）：当没存。
 *
 * 新存储导入（`LegacySettingsMigration`）和旧存储读取（USE_NEW_STORE 关时的 `SharedPrefsTransitStore`）都走这里，
 * 两边读出来的数据源一样。
 */
object LegacyDataSource {
    const val LEGACY_AGGREGATED_MODE = "ALL"
    const val LEGACY_MYGOLBS_FIRST = "MYGOLBS_FIRST"

    /** 返回 [EngineMode] 的名字；返回 null 表示没有可用的旧配置 */
    fun resolve(engineMode: String?, aggregatedPriority: String?): String? {
        EngineMode.entries.firstOrNull { it.name == engineMode }?.let { return it.name }
        if (engineMode != null && engineMode != LEGACY_AGGREGATED_MODE) return null
        return when {
            aggregatedPriority == LEGACY_MYGOLBS_FIRST -> EngineMode.MYGOLBS.name
            engineMode == LEGACY_AGGREGATED_MODE -> EngineMode.CHELAILE.name
            else -> null
        }
    }
}
