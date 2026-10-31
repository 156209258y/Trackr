package com.trackr.core.domain.platform

import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitCodeProvider
import com.trackr.core.model.UpdateCheckResult
import com.trackr.core.model.UserFix
import kotlinx.coroutines.flow.Flow

/*
 * 平台能力接口（重构计划 §2.1、§2.8）。
 *
 * commonMain 里的 ViewModel / 引擎只认这些接口；实现放平台层：
 * Android 在 :androidApp（以后是各模块的 androidMain），iOS 在 iosMain。
 * 测试用 :core:testing 里的 Fake。
 *
 * 墙钟直接用标准库的 `kotlin.time.Clock`（它本身就是接口，有 `Clock.System`），这里不再重复定义；
 * 定位年龄这类要用单调时钟的地方用 [MonotonicClock]。
 */

/**
 * 单调时钟（毫秒），不受用户改系统时间影响；进程重启后归零，所以读数不能落盘。
 * Android：`SystemClock.elapsedRealtime()`，和 `Location.elapsedRealtimeNanos` 同一个时基，[UserFix.elapsedMillis] 用它。
 */
fun interface MonotonicClock {
    fun elapsedMillis(): Long
}

/** 追踪服务的控制入口（Android：前台服务；iOS：App 在前台时轮询） */
interface TrackingController {

    /** 结束追踪：停止追踪服务；分组追踪时同时清掉换乘组状态 */
    fun stopTracking()

    /** 把带精度和定位时刻的手机定位喂给换乘引擎（下车站辅助判定用） */
    fun updateUserFix(fix: UserFix)
}

/** 朗读（长辈版用）。Android：TextToSpeech；iOS：AVSpeechSynthesizer */
interface Announcer {

    /** 朗读一段文字；正在读的会被打断 */
    fun speak(text: String)

    /** 停止朗读 */
    fun stop()
}

/** 打开乘车码。Android：Intent 打开支付宝 / 云闪付；iOS：URL Scheme */
interface TransitCodeLauncher {

    /** 打开指定渠道的乘车码；打不开（没装、[TransitCodeProvider.ASK_ALWAYS]）返回 false */
    fun open(provider: TransitCodeProvider): Boolean

    /** 这个渠道的 App 是否装了 */
    fun isInstalled(provider: TransitCodeProvider): Boolean
}

/**
 * 手机定位（WGS-84）。Android：系统 LocationManager；iOS：CLLocationManager。
 * 上车 / 下车判定只认精确定位，模糊定位精度在公里级。
 */
interface LocationSource {

    /** 按档位持续定位；[LocationCadence.Tier.OFF] 时不出数据。收集结束即停止定位 */
    fun fixes(tier: LocationCadence.Tier): Flow<UserFix>

    /** 系统缓存的最近一次定位，没有时为 null（年龄由调用方用 [MonotonicClock] 判断） */
    fun lastKnown(): UserFix?

    /** 是否有精确定位权限 */
    fun hasPrecisePermission(): Boolean
}

/** 语音输入（长辈版搜索用）。Android：RecognizerIntent；iOS：SFSpeechRecognizer */
interface SpeechInput {

    /** 听一句话，返回识别出的文字；用户取消、没听清或没权限时返回 null */
    suspend fun listen(): String?
}

/**
 * 定时提醒的系统闹钟。Android：AlarmManager；iOS：UNCalendarNotificationTrigger。
 * 提醒数据存在仓库里，这里只管排 / 取消闹钟。
 */
interface ReminderAlarms {

    /** 排一个提醒的下一次触发；提醒已关闭时等同 [cancel] */
    fun schedule(reminder: ScheduledReminder)

    /** 取消一个提醒的闹钟 */
    fun cancel(reminderId: String)

    /** 按列表重排：开着的排，关着的取消（开机、改时间、改时区后调用） */
    fun rescheduleAll(reminders: List<ScheduledReminder>)

    /** 能否排精确闹钟（Android 12+ 要用户授权；只有精确闹钟到点才直接开追踪） */
    fun canScheduleExact(): Boolean

    /** 这个提醒上次排的触发时刻（墙钟毫秒）；从没排过或已取消时为 null */
    fun lastScheduledTriggerMillis(reminderId: String): Long?
}

/** 应用内更新检查（只有 Android 有；iOS 走 App Store） */
interface UpdateChecker {

    /**
     * 检查是否有新版本
     * @param currentVersionCode 当前安装版本的 versionCode
     * @param currentVersionName 当前安装版本的 versionName
     */
    suspend fun checkForUpdates(
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckResult
}

/** 「高德 Key」页显示的应用标识，帮用户在高德控制台绑定 Key */
interface AppSigningInfo {

    /** Android：包名；iOS：Bundle ID */
    val appId: String

    /** Android：当前签名证书的 SHA-1（冒号分隔的大写十六进制）；iOS 没有，为 null */
    fun certificateSha1(): String?
}
