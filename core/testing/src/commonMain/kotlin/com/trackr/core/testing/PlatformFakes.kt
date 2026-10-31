package com.trackr.core.testing

import com.trackr.core.domain.platform.Announcer
import com.trackr.core.domain.platform.AppSigningInfo
import com.trackr.core.domain.platform.LocationSource
import com.trackr.core.domain.platform.ReminderAlarms
import com.trackr.core.domain.platform.SpeechInput
import com.trackr.core.domain.platform.TrackingController
import com.trackr.core.domain.platform.TransitCodeLauncher
import com.trackr.core.domain.platform.UpdateChecker
import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.model.UpdateCheckResult
import com.trackr.core.model.UserFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

/*
 * :core:domain 平台能力接口的 Fake：只记下调用，结果由测试设定。
 * 都不是线程安全的，只在单线程测试（runTest）里用。
 */

class FakeTrackingController : TrackingController {
    var stopCount: Int = 0
        private set
    val fixes: MutableList<UserFix> = mutableListOf()

    override fun stopTracking() {
        stopCount++
    }

    override fun updateUserFix(fix: UserFix) {
        fixes += fix
    }
}

class FakeAnnouncer : Announcer {
    val spoken: MutableList<String> = mutableListOf()
    var stopCount: Int = 0
        private set

    override fun speak(text: String) {
        spoken += text
    }

    override fun stop() {
        stopCount++
    }
}

class FakeTransitCodeLauncher(
    var installed: Set<TransitCodeProvider> = setOf(TransitCodeProvider.ALIPAY, TransitCodeProvider.UNIONPAY)
) : TransitCodeLauncher {
    val opened: MutableList<TransitCodeProvider> = mutableListOf()

    override fun open(provider: TransitCodeProvider): Boolean {
        if (provider == TransitCodeProvider.ASK_ALWAYS || provider !in installed) return false
        opened += provider
        return true
    }

    override fun isInstalled(provider: TransitCodeProvider): Boolean = provider in installed
}

/**
 * 定位 Fake：[emit] 发一个定位给正在收集的 [fixes]；档位为 OFF 时丢掉（和接口约定一致）。
 * 收集时记下请求的档位，方便断言定位频率。
 */
class FakeLocationSource(
    var lastKnownFix: UserFix? = null,
    var precisePermission: Boolean = true
) : LocationSource {
    private val live = MutableSharedFlow<UserFix>(extraBufferCapacity = 64)
    val requestedTiers: MutableList<LocationCadence.Tier> = mutableListOf()

    override fun fixes(tier: LocationCadence.Tier): Flow<UserFix> {
        requestedTiers += tier
        return if (tier == LocationCadence.Tier.OFF) emptyFlow() else live
    }

    /** 发出一个定位，同时更新 [lastKnownFix]；没人收集时只更新 [lastKnownFix] */
    fun emit(fix: UserFix) {
        lastKnownFix = fix
        live.tryEmit(fix)
    }

    override fun lastKnown(): UserFix? = lastKnownFix

    override fun hasPrecisePermission(): Boolean = precisePermission
}

/** 语音输入 Fake：依次返回 [answers] 里的结果，用完后返回 null */
class FakeSpeechInput(answers: List<String?> = emptyList()) : SpeechInput {
    private val queue = ArrayDeque(answers)
    var listenCount: Int = 0
        private set

    override suspend fun listen(): String? {
        listenCount++
        return queue.removeFirstOrNull()
    }
}

/**
 * 闹钟 Fake：[schedule] 记下「下一次触发时刻」，时刻由 [nextTriggerOf] 算（默认用提醒的时分凑一个假时刻，测试一般自己传）。
 */
class FakeReminderAlarms(
    var exactAllowed: Boolean = true,
    private val nextTriggerOf: (ScheduledReminder) -> Long = { it.hour * 3_600_000L + it.minute * 60_000L }
) : ReminderAlarms {
    /** 当前排着的闹钟：提醒 ID → 计划触发时刻 */
    val scheduled: MutableMap<String, Long> = linkedMapOf()

    override fun schedule(reminder: ScheduledReminder) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        scheduled[reminder.id] = nextTriggerOf(reminder)
    }

    override fun cancel(reminderId: String) {
        scheduled.remove(reminderId)
    }

    override fun rescheduleAll(reminders: List<ScheduledReminder>) {
        reminders.forEach { schedule(it) }
    }

    override fun canScheduleExact(): Boolean = exactAllowed

    override fun lastScheduledTriggerMillis(reminderId: String): Long? = scheduled[reminderId]
}

class FakeUpdateChecker(var result: UpdateCheckResult) : UpdateChecker {
    var checkCount: Int = 0
        private set

    override suspend fun checkForUpdates(currentVersionCode: Int, currentVersionName: String): UpdateCheckResult {
        checkCount++
        return result
    }
}

class FakeAppSigningInfo(
    override val appId: String = "com.github.u5151561.trackr",
    private val sha1: String? = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD"
) : AppSigningInfo {
    override fun certificateSha1(): String? = sha1
}
