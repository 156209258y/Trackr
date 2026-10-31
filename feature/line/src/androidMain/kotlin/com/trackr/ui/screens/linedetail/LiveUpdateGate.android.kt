package com.trackr.ui.screens.linedetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.trackr.ui.platform.LiveUpdateSettings
import org.koin.compose.koinInject

/** 判断和跳转原样转调 [LiveUpdateSettings]（:androidApp 的厂商适配），和旧页面的写法一致 */
@Composable
internal actual fun rememberLiveUpdateGate(): LiveUpdateGate {
    val context = LocalContext.current
    val settings: LiveUpdateSettings = koinInject()
    return remember(context, settings) {
        object : LiveUpdateGate {
            override fun blockingGuide(): LiveUpdateGuide? {
                val xiaomiFocusBlocked = settings.isXiaomiRom() && !settings.canShowXiaomiFocus(context)
                val promotionBlocked = !xiaomiFocusBlocked && !settings.canPromote(context)
                return when {
                    xiaomiFocusBlocked -> LiveUpdateGuide.XIAOMI_FOCUS
                    promotionBlocked -> LiveUpdateGuide.PROMOTED_ONGOING
                    else -> null
                }
            }

            override fun openSettings(guide: LiveUpdateGuide) {
                when (guide) {
                    LiveUpdateGuide.XIAOMI_FOCUS -> settings.openXiaomiNotificationSettings(context)
                    LiveUpdateGuide.PROMOTED_ONGOING -> settings.openPromotionSettings(context)
                }
            }

            override fun deviceHint(guide: LiveUpdateGuide): String = when {
                guide == LiveUpdateGuide.XIAOMI_FOCUS -> "检测到您正在使用小米澎湃OS（Xiaomi HyperOS）。"
                settings.isColorOs() -> "检测到您正在使用 OPPO ColorOS / 一加 / 真我 系统。"
                else -> "检测到您的系统支持 Android 16 实时活动（Live Updates）。"
            }
        }
    }
}
