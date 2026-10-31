package com.trackr.ui.screens.linedetail

import androidx.compose.runtime.Composable

/** iOS 没有要先开的实时活动权限（Live Activity 在第 36 步），直接开始追踪 */
private object NoLiveUpdateGate : LiveUpdateGate {
    override fun blockingGuide(): LiveUpdateGuide? = null
    override fun openSettings(guide: LiveUpdateGuide) = Unit
    override fun deviceHint(guide: LiveUpdateGuide): String = ""
}

@Composable
internal actual fun rememberLiveUpdateGate(): LiveUpdateGate = NoLiveUpdateGate
