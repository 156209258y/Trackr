package com.trackr.service

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.trackr.core.data.repository.TrackingRepository
import com.trackr.core.domain.platform.TrackingController
import com.trackr.core.model.TrackingTarget
import com.trackr.core.model.UserFix

/**
 * `:core:domain` 里 [TrackingController] 的 Android 实现：命令都写进 [TrackingRepository]，
 * 开始追踪时再用 sessionId 启动前台服务 [BusTrackingService]（服务收集仓库里的会话，结束 / 切程不用再发 Intent）。
 *
 * @param starter 启动前台服务（测试替换）；后台不允许时抛 IllegalStateException（Android 12+ 是
 *   ForegroundServiceStartNotAllowedException）或 SecurityException
 */
class AndroidTrackingController(
    context: Context,
    private val repository: TrackingRepository,
    private val starter: (Context, String) -> Unit = { ctx, sessionId ->
        ContextCompat.startForegroundService(ctx, BusTrackingService.startIntent(ctx, sessionId))
    }
) : TrackingController {

    private val appContext: Context = context.applicationContext ?: context

    override fun start(target: TrackingTarget): Boolean {
        val previous = repository.session.value
        val session = repository.start(target) ?: return false
        return try {
            starter(appContext, session.sessionId)
            true
        } catch (e: IllegalStateException) {
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Cannot start tracking service (fgsNotAllowed=$notAllowed)", e)
            repository.restore(session, previous)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot start tracking service", e)
            repository.restore(session, previous)
            false
        }
    }

    override fun stopTracking() {
        repository.stop()
    }

    override fun confirmBoarded() {
        repository.confirmBoarded()
    }

    override fun chooseBoardedBus(busId: String?) {
        repository.chooseBoardedBus(busId)
    }

    override fun switchToNextLeg() {
        repository.switchToNextLeg()
    }

    override fun cycleThreshold() {
        repository.cycleThreshold()
    }

    override fun updateUserFix(fix: UserFix) {
        repository.updateUserFix(fix)
    }

    private companion object {
        const val TAG = "TrackingController"
    }
}
