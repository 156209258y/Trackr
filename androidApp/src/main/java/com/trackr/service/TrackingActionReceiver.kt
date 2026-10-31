package com.trackr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trackr.core.data.repository.TrackingRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 接收常驻通知栏 Live Updates 交互按钮的广播，直接改 [TrackingRepository]；
 * 追踪服务收集仓库里的会话，自己跟着停 / 切程 / 刷新通知。
 */
class TrackingActionReceiver : BroadcastReceiver(), KoinComponent {

    private val trackingRepository: TrackingRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_TRACKING -> trackingRepository.stop()
            ACTION_CYCLE_THRESHOLD -> trackingRepository.cycleThreshold()
            ACTION_CONFIRM_BOARDED -> trackingRepository.confirmBoarded()
            ACTION_NEXT_LEG -> trackingRepository.switchToNextLeg()
        }
    }

    companion object {
        const val ACTION_STOP_TRACKING = "com.trackr.action.STOP_TRACKING"
        const val ACTION_CYCLE_THRESHOLD = "com.trackr.action.CYCLE_THRESHOLD"
        const val ACTION_CONFIRM_BOARDED = "com.trackr.action.CONFIRM_BOARDED"
        const val ACTION_NEXT_LEG = "com.trackr.action.NEXT_LEG"
    }
}
