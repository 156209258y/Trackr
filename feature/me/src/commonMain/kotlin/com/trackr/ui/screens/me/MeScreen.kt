@file:OptIn(ExperimentalMaterial3Api::class)

package com.trackr.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_chevron_right
import com.trackr.core.designsystem.resources.ic_clock
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.model.EngineMode
import com.trackr.core.model.ScheduledReminder
import com.trackr.core.model.TransitCodeProvider
import org.jetbrains.compose.resources.vectorResource

/**
 * 「我的」页（v3）：分组「出行」「通知」「地图」「关于」，长辈版的「显示」组位置预留。
 * 无状态，只有哪个对话框 / Sheet 开着是本地状态。
 */
@Composable
fun MeScreen(
    state: MeUiState,
    onEvent: (MeEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showTransitCodeDialog by rememberSaveable { mutableStateOf(false) }
    var showAlertThresholdDialog by rememberSaveable { mutableStateOf(false) }
    var showRemindersSheet by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(state.userMessages, snackbarHostState) { onEvent(MeEvent.MessageShown(it)) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(text = "我的", fontWeight = FontWeight.Bold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 长辈版：第 30 步接 SettingsRepository.uiMode，现在 elderMode 恒为 null，这组不显示
            state.elderMode?.let { elderMode ->
                item(key = "group_display", contentType = "settings_group") {
                    SettingsGroup(title = "显示") {
                        SwitchRow(
                            title = "长辈版",
                            subtitle = "字更大，按钮更大",
                            checked = elderMode,
                            onCheckedChange = { onEvent(MeEvent.SetElderMode(it)) }
                        )
                    }
                }
            }

            item(key = "group_trip", contentType = "settings_group") {
                SettingsGroup(title = "出行") {
                    EngineModeRow(
                        selected = state.engineMode,
                        onSelect = { onEvent(MeEvent.SetEngineMode(it)) }
                    )
                    GroupDivider()
                    SettingRow(
                        title = "乘车码",
                        value = transitCodeLabel(state.preferredTransitCode),
                        onClick = { showTransitCodeDialog = true }
                    )
                    GroupDivider()
                    SettingRow(
                        title = "到站前提醒",
                        subtitle = "车离上车站还剩几站时震动提醒",
                        value = alertThresholdLabel(state.alertThresholdStops),
                        onClick = { showAlertThresholdDialog = true }
                    )
                    GroupDivider()
                    SettingRow(
                        title = "定时提醒",
                        subtitle = state.remindersSubtitle,
                        value = if (state.reminders.isEmpty()) "没有" else "${state.reminders.size} 个",
                        onClick = { showRemindersSheet = true }
                    )
                }
            }

            item(key = "group_notification", contentType = "settings_group") {
                SettingsGroup(title = "通知") {
                    SwitchRow(
                        title = "实时通知",
                        subtitle = "追踪时在通知栏显示车还有几站",
                        checked = state.isLiveNotificationEnabled,
                        onCheckedChange = { onEvent(MeEvent.SetLiveNotificationEnabled(it)) }
                    )
                    GroupDivider()
                    SwitchRow(
                        title = "锁屏显示追踪",
                        subtitle = if (state.showOnLockScreen) {
                            "锁屏能看到线路和还有几站"
                        } else {
                            "锁屏只显示「追踪中」"
                        },
                        checked = state.showOnLockScreen,
                        onCheckedChange = { onEvent(MeEvent.SetShowOnLockScreen(it)) }
                    )
                    state.liveUpdate?.let { liveUpdate ->
                        GroupDivider()
                        SettingRow(
                            title = "状态栏胶囊",
                            subtitle = if (liveUpdate.supported) {
                                liveUpdate.systemName
                            } else {
                                "这台手机不支持，只在通知栏显示"
                            },
                            value = when {
                                !liveUpdate.supported -> "通知设置"
                                liveUpdate.allowed -> "已开启"
                                else -> "去开启"
                            },
                            valueColor = if (liveUpdate.supported && !liveUpdate.allowed) {
                                TrackrTheme.colors.warn
                            } else {
                                null
                            },
                            onClick = { onEvent(MeEvent.OpenLiveUpdateSettings) }
                        )
                    }
                }
            }

            item(key = "group_map", contentType = "settings_group") {
                SettingsGroup(title = "地图") {
                    SettingRow(
                        title = "高德 Key",
                        subtitle = state.amapKeyStatus,
                        onClick = { onEvent(MeEvent.OpenAmapKey) },
                        modifier = Modifier.testTag("row_amap_key"),
                        subtitleModifier = Modifier.testTag("text_amap_key_status")
                    )
                }
            }

            item(key = "group_about", contentType = "settings_group") {
                SettingsGroup(title = "关于") {
                    SettingRow(
                        title = "关于追客",
                        value = state.versionName?.let { "版本 $it" },
                        onClick = { onEvent(MeEvent.OpenAbout) }
                    )
                }
            }
        }
    }

    if (showTransitCodeDialog) {
        ChoiceDialog(
            title = "乘车码",
            options = TransitCodeProvider.entries,
            selected = state.preferredTransitCode,
            label = ::transitCodeLabel,
            onSelect = {
                onEvent(MeEvent.SetTransitCode(it))
                showTransitCodeDialog = false
            },
            onDismiss = { showTransitCodeDialog = false }
        )
    }

    if (showAlertThresholdDialog) {
        ChoiceDialog(
            title = "到站前提醒",
            options = ALERT_THRESHOLD_CHOICES,
            selected = state.alertThresholdStops,
            label = ::alertThresholdLabel,
            onSelect = {
                onEvent(MeEvent.SetAlertThresholdStops(it))
                showAlertThresholdDialog = false
            },
            onDismiss = { showAlertThresholdDialog = false }
        )
    }

    if (showRemindersSheet) {
        RemindersSheet(
            reminders = state.reminders,
            onDismiss = { showRemindersSheet = false },
            onToggle = { id, enabled -> onEvent(MeEvent.ToggleReminder(id, enabled)) },
            onOpen = { id ->
                showRemindersSheet = false
                onEvent(MeEvent.OpenReminder(id))
            }
        )
    }
}

private fun transitCodeLabel(provider: TransitCodeProvider): String = when (provider) {
    TransitCodeProvider.ASK_ALWAYS -> "每次询问"
    TransitCodeProvider.ALIPAY -> "支付宝"
    TransitCodeProvider.UNIONPAY -> "云闪付"
}

private fun alertThresholdLabel(stops: Int): String = "提前 $stops 站"

/** 一组设置：小号主色组标题 + 圆角卡片；行之间由调用方放 [GroupDivider] */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            content = content
        )
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 行的左边：标题 + 可选副标题 */
@Composable
private fun RowTexts(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleModifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = subtitleModifier.padding(top = 2.dp)
            )
        }
    }
}

/** 可点的行：标题、副标题，右边值 + 箭头 */
@Composable
private fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    subtitleModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowTexts(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
            subtitleModifier = subtitleModifier
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 开关行：整行可点 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 「数据来源」：车来了 / 掌上公交 两段 */
@Composable
private fun EngineModeRow(
    selected: EngineMode,
    onSelect: (EngineMode) -> Unit
) {
    val options = listOf(EngineMode.CHELAILE to "车来了", EngineMode.MYGOLBS to "掌上公交")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowTexts(title = "数据来源", subtitle = null, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {},
                    label = { Text(text = label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

/** 单选对话框：点选项即选中并关闭；只有「取消」按钮 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(text = label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 定时提醒列表：开关在这里，改和删在编辑页 */
@Composable
private fun RemindersSheet(
    reminders: List<ScheduledReminder>,
    onDismiss: () -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onOpen: (id: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "定时提醒",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${reminders.size} 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (reminders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_clock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "还没有定时提醒",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "在线路页选好上车站，点时钟图标新建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(
                    items = reminders,
                    key = { it.id },
                    contentType = { "reminder" }
                ) { reminder ->
                    ReminderItem(
                        reminder = reminder,
                        onToggle = { onToggle(reminder.id, it) },
                        onClick = { onOpen(reminder.id) }
                    )
                }
            }
        }
    }
}

/** 一条提醒：时间、重复标签、线路、上车站；关着的变淡 */
@Composable
private fun ReminderItem(
    reminder: ScheduledReminder,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_${reminder.id}")
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (reminder.enabled) 1f else 0.5f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reminder.formatTime(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = shortRepeatText(reminder.daysOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${reminder.lineName} → ${reminder.endStopName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = buildString {
                    append("${reminder.targetStopName} 上车")
                    if (reminder.autoStartLiveTracking) append(" · 到点自动追踪")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = reminder.enabled, onCheckedChange = onToggle)
    }
}
