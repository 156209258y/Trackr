package com.trackr.data.repository

import android.content.Context
import com.trackr.core.data.local.ReminderStore
import com.trackr.core.domain.platform.ReminderAlarms
import com.trackr.core.model.ScheduledReminder
import com.trackr.data.local.SharedPrefsReminderStore
import com.trackr.util.ReminderAlarmScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 定时提醒仓库。
 *
 * 读写经 [ReminderStore]（第 9 步）：USE_NEW_STORE 开时是 Room `reminder` 表，关时是原来的 SharedPreferences
 * `trackr_scheduled_reminders`（JSON 损坏先备份原文，148d67c）。系统闹钟经 [ReminderAlarms]。
 * 「写库 + 排闹钟」在同一把锁里，先写库、再排闹钟（P4）；排闹钟失败时开机 / 改时间的接收器会再排一次。
 */
class ScheduledReminderRepository(
    private val store: ReminderStore,
    private val alarms: ReminderAlarms,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IScheduledReminderRepository {

    /** 旧存储（SharedPreferences）+ 旧的闹钟状态文件；现有测试和 USE_NEW_STORE 关闭时用 */
    constructor(context: Context, ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
        this(SharedPrefsReminderStore(context), ReminderAlarmScheduler(context), ioDispatcher)

    /** 串行化所有读改写，避免 UI 与提醒接收器并发调用时丢失更新 */
    private val mutex = Mutex()

    private val _reminders = MutableStateFlow<List<ScheduledReminder>>(runBlocking { store.load() })
    override val reminders: StateFlow<List<ScheduledReminder>> = _reminders.asStateFlow()

    override suspend fun saveReminder(reminder: ScheduledReminder) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _reminders.value.toMutableList()
            val index = current.indexOfFirst { it.id == reminder.id }
            if (index >= 0) {
                current[index] = reminder
            } else {
                current.add(reminder)
            }
            persistReminders(current)
            alarms.schedule(reminder)
        }
    }

    override suspend fun deleteReminder(id: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _reminders.value.filterNot { it.id == id }
            persistReminders(current)
            alarms.cancel(id)
        }
    }

    override suspend fun toggleReminder(id: String, enabled: Boolean) = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _reminders.value.map { reminder ->
                if (reminder.id == id) reminder.copy(enabled = enabled) else reminder
            }
            persistReminders(current)
            val updated = current.find { it.id == id }
            if (updated != null) {
                if (enabled) {
                    alarms.schedule(updated)
                } else {
                    alarms.cancel(id)
                }
            }
        }
    }

    override fun getReminderById(id: String): ScheduledReminder? {
        return _reminders.value.find { it.id == id }
    }

    private suspend fun persistReminders(list: List<ScheduledReminder>) {
        _reminders.value = list
        store.save(list)
    }

    companion object {
        /** 旧存储里损坏 JSON 的备份 key 前缀（实现在 [SharedPrefsReminderStore]） */
        internal const val KEY_CORRUPT_BACKUP_PREFIX = SharedPrefsReminderStore.KEY_CORRUPT_BACKUP_PREFIX
    }
}
