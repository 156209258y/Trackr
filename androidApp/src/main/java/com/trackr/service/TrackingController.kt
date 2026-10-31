package com.trackr.service

import android.content.Context
import com.trackr.core.domain.platform.TrackingController
import com.trackr.core.model.UserFix

/**
 * `:core:domain` 里 [TrackingController] 的 Android 实现，原样转调 [TransferAutoSwitchManager]。
 * 第 12 步换成走 `TrackingRepository` + 前台服务。
 */
class AndroidTrackingController(context: Context) : TrackingController {

    private val appContext: Context = context.applicationContext ?: context

    override fun stopTracking() {
        TransferAutoSwitchManager.stopGroupTracking(appContext)
    }

    override fun updateUserFix(fix: UserFix) {
        TransferAutoSwitchManager.updateUserFix(fix)
    }
}
