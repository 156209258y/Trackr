package com.trackr.core.network.remote.common

/**
 * 运营时段判断。
 *
 * 掌上公交的站点接口在末班车之后仍然回「等待发车」，和车来了的「末班已过」不一样。
 * 上游其实给了每个方向的首末班时段（CMD=105 的 `seutimes` / `sedtimes`，
 * 对应 CMD=103 的 `firstLast`），拿它和当前北京时间比一下就能分清：
 * - 还在时段内：保留上游文案（「3站」「即将到站」）；
 * - 末班之后：显示「末班已过」；
 * - 首班之前：保留「等待发车」。
 */
object ServiceHours {

    data class Window(val first: String, val last: String)

    enum class State {
        /** 现在在某个时段内 */
        IN_SERVICE,

        /** 今天还有时段没开始（首班前或午休） */
        BEFORE_FIRST,

        /** 今天的时段都结束了 */
        AFTER_LAST,

        /** 上游没给首末班，判不了 */
        UNKNOWN
    }

    /**
     * @param nowHHMM 当前北京时间，格式 HH:mm
     * @param windows 首末班时段；[Window.first] 可以晚于 [Window.last]（跨零点，如 22:40~01:30）
     */
    fun state(nowHHMM: String, windows: List<Window>): State {
        val now = TimetableRules.normalizeClock(nowHHMM) ?: return State.UNKNOWN
        val valid = windows.mapNotNull { window ->
            val first = TimetableRules.normalizeClock(window.first) ?: return@mapNotNull null
            val last = TimetableRules.normalizeClock(window.last) ?: return@mapNotNull null
            first to last
        }
        if (valid.isEmpty()) return State.UNKNOWN

        var hasFutureWindow = false
        for ((first, last) in valid) {
            if (first <= last) {
                if (now >= first && now <= last) return State.IN_SERVICE
                if (now < first) hasFutureWindow = true
            } else {
                // 跨零点：22:40~01:30，now >= 22:40 或 now <= 01:30 都算在时段内
                if (now >= first || now <= last) return State.IN_SERVICE
                // 01:30 ~ 22:40 之间：今晚的班还没开始
                hasFutureWindow = true
            }
        }
        return if (hasFutureWindow) State.BEFORE_FIRST else State.AFTER_LAST
    }
}
