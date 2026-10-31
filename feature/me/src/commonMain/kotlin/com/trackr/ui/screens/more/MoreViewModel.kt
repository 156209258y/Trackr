package com.trackr.ui.screens.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.core.data.repository.AmapKeyRepository
import com.trackr.core.data.repository.SettingsRepository
import com.trackr.core.model.EngineMode
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitCodeProvider
import com.trackr.data.repository.IScheduledReminderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 更多页 UI 状态
 */
data class MoreUiState(
    val engineMode: EngineMode = EngineMode.CHELAILE,
    val nearbyStopsCount: Int = 4,
    val preferredTransitCode: TransitCodeProvider = TransitCodeProvider.ASK_ALWAYS,
    val isLiveNotificationEnabled: Boolean = true,
    val reminders: List<ScheduledReminder> = emptyList()
)

class MoreViewModel(
    private val repository: SettingsRepository,
    private val reminderRepository: IScheduledReminderRepository,
    private val amapKeys: AmapKeyRepository
) : ViewModel() {

    val engineMode: StateFlow<EngineMode> = repository.engineMode
    val nearbyStopsCount: StateFlow<Int> = repository.nearbyStopsCount
    val preferredTransitCode: StateFlow<TransitCodeProvider> = repository.preferredTransitCodeProvider
    val isLiveNotificationEnabled: StateFlow<Boolean> = repository.isLiveNotificationEnabled
    val reminders: StateFlow<List<ScheduledReminder>> = reminderRepository.reminders

    val uiState: StateFlow<MoreUiState> = combine(
        engineMode,
        nearbyStopsCount,
        preferredTransitCode,
        isLiveNotificationEnabled,
        reminders
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        MoreUiState(
            engineMode = args[0] as EngineMode,
            nearbyStopsCount = args[1] as Int,
            preferredTransitCode = args[2] as TransitCodeProvider,
            isLiveNotificationEnabled = args[3] as Boolean,
            reminders = args[4] as List<ScheduledReminder>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MoreUiState(
            engineMode = repository.engineMode.value,
            nearbyStopsCount = repository.nearbyStopsCount.value,
            preferredTransitCode = repository.preferredTransitCodeProvider.value,
            isLiveNotificationEnabled = repository.isLiveNotificationEnabled.value,
            reminders = reminderRepository.reminders.value
        )
    )

    fun setLiveNotificationEnabled(enabled: Boolean) {
        repository.setLiveNotificationEnabled(enabled)
    }

    /** 锁屏显示追踪进度（D3）。正式位置在第 25 步的「我的」页 */
    val showOnLockScreen: StateFlow<Boolean> = repository.showOnLockScreen

    fun setShowOnLockScreen(enabled: Boolean) {
        repository.setShowOnLockScreen(enabled)
    }

    fun setEngineMode(mode: EngineMode) {
        repository.setEngineMode(mode)
    }

    fun setNearbyStopsCount(count: Int) {
        val clamped = count.coerceIn(1, 16)
        repository.setNearbyStopsCount(clamped)
    }

    fun setPreferredTransitCode(provider: TransitCodeProvider) {
        viewModelScope.launch {
            repository.setPreferredTransitCodeProvider(provider)
        }
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepository.toggleReminder(id, enabled)
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(id)
        }
    }

    // 高德 Key 两槽（第 11 步从路线规划页挪来）：各写各的，互不回落；不进 [uiState]，「高德 Key」区块单独收集

    /** 用户填的 Android 槽（地图 / SDK） */
    val amapAndroidKey: StateFlow<String?> = amapKeys.androidKey

    /** 用户填的 Web 槽（云端算路 / 输入提示） */
    val amapWebKey: StateFlow<String?> = amapKeys.webKey

    /** 有没有可用的 Key（含构建时注入的）：决定底栏有没有「路线规划」 */
    val hasMapKey: StateFlow<Boolean> = amapKeys.hasMapKey

    /** 有没有 Android Key：路线规划页画不画地图 */
    val canShowMap: StateFlow<Boolean> = amapKeys.canShowMap

    /** 只写 Android 槽；空串等于清空。改完由 `AmapSdkKeySync` 注入 SDK */
    fun saveAmapAndroidKey(key: String) {
        amapKeys.setAndroidKey(key.trim().ifBlank { null })
    }

    /** 只写 Web 槽；空串等于清空 */
    fun saveAmapWebKey(key: String) {
        amapKeys.setWebKey(key.trim().ifBlank { null })
    }
}
