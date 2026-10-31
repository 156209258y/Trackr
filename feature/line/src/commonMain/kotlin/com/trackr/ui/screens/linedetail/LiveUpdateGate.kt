package com.trackr.ui.screens.linedetail

import androidx.compose.runtime.Composable

/** 开始追踪前要引导去开的系统实时活动权限 */
enum class LiveUpdateGuide {
    /** 小米焦点通知（超级岛） */
    XIAOMI_FOCUS,

    /** Android 16 实时活动 / 置顶通知（含 OPPO 流体云） */
    PROMOTED_ONGOING
}

/**
 * 开始追踪前检查系统实时活动权限（原来线路页直接调 `LiveUpdateSettings` + `Context`）。
 * Android：小米没开焦点通知、或系统不允许置顶通知时返回要引导的项；iOS 不需要，永远放行。
 */
interface LiveUpdateGate {
    /** 现在开始追踪前要先引导哪一项；null = 直接开始 */
    fun blockingGuide(): LiveUpdateGuide?

    /** 打开 [guide] 对应的系统设置页 */
    fun openSettings(guide: LiveUpdateGuide)

    /** 引导弹窗里的机型说明 */
    fun deviceHint(guide: LiveUpdateGuide): String
}

@Composable
internal expect fun rememberLiveUpdateGate(): LiveUpdateGate
