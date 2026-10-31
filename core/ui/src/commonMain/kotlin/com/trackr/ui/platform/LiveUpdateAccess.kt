package com.trackr.ui.platform

/**
 * 「我的」页「状态栏胶囊」那一行要的系统状态（第 25 步）。
 *
 * Android 实现在 `:androidApp` 的 PlatformBridgeModule，转调原来的厂商适配对象（实时活动、小米焦点通知、OPPO 流体云），
 * 判断和跳转分支和第 17 步的「更多」页一样；iOS 不绑定（没有这一行）。
 */
interface LiveUpdateAccess {

    /** 现在的状态；每次回到前台重查（用户可能刚去系统设置里开过） */
    fun status(): LiveUpdateStatus

    /**
     * 去系统设置：系统不支持 → 应用通知设置；没开 → 开权限的页面；已开 → 小米 / OPPO 各自的设置页，别的系统是应用通知设置
     */
    fun openSettings()
}

/**
 * @param supported 系统支不支持状态栏胶囊（Android 16 实时活动、小米焦点通知、OPPO 流体云三者之一）
 * @param allowed 用户开没开权限；[supported] 为 false 时恒为 false
 * @param systemName 给用户看的系统功能名，比如「小米焦点通知」；不支持时为空串
 */
data class LiveUpdateStatus(
    val supported: Boolean,
    val allowed: Boolean,
    val systemName: String
)
