package com.trackr.ui.screens.linedetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackr.core.designsystem.component.DataSourceBadge
import com.trackr.core.designsystem.component.DataSourceBadgeStyle
import com.trackr.core.designsystem.component.FullscreenErrorView
import com.trackr.core.designsystem.component.FullscreenLoadingView
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.action_back
import com.trackr.core.designsystem.resources.action_end_tracking
import com.trackr.core.designsystem.resources.action_favorite
import com.trackr.core.designsystem.resources.action_retry
import com.trackr.core.designsystem.resources.action_unfavorite
import com.trackr.core.designsystem.resources.ic_back
import com.trackr.core.designsystem.resources.ic_clock
import com.trackr.core.designsystem.resources.ic_play
import com.trackr.core.designsystem.resources.ic_qr_code
import com.trackr.core.designsystem.resources.ic_star
import com.trackr.core.designsystem.resources.ic_star_filled
import com.trackr.core.designsystem.resources.ic_swap
import com.trackr.core.designsystem.resources.transit_code
import com.trackr.core.designsystem.theme.busColors
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusStop
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.LineOperatingState
import com.trackr.core.model.LiveBus
import com.trackr.core.model.TransitCodeProvider
import com.trackr.feature.line.resources.Res as LineRes
import com.trackr.feature.line.resources.line_detail_error
import com.trackr.feature.line.resources.line_detail_fare
import com.trackr.feature.line.resources.line_detail_hours
import com.trackr.feature.line.resources.line_detail_loading
import com.trackr.feature.line.resources.line_detail_reminder
import com.trackr.feature.line.resources.line_detail_reverse
import com.trackr.feature.line.resources.line_detail_stop_count
import com.trackr.feature.line.resources.line_detail_timetable
import com.trackr.feature.line.resources.line_detail_track_this
import com.trackr.ui.components.message.HostMessage
import com.trackr.ui.components.message.UserMessageHost
import com.trackr.ui.components.ScheduledReminderDialog
import com.trackr.ui.components.TransitCodeBottomSheet
import com.trackr.ui.components.routeRailItemIndex
import com.trackr.ui.components.routeRailItems
import com.trackr.ui.components.timetable.TimetableBottomSheet
import com.trackr.ui.screens.linedetail.components.BusFollowContent
import com.trackr.ui.screens.linedetail.components.BusListContent
import com.trackr.ui.screens.linedetail.components.FavoriteGroupPickerSheet
import com.trackr.ui.screens.linedetail.components.StationArrivalContent
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** 线路详情页发出的事件（无状态页面只发事件，见 [LineDetailRoute]） */
sealed interface LineDetailEvent {
    data object Back : LineDetailEvent
    /** @param fromPull 下拉触发（画下拉圈）；false = 错误条「重试」 */
    data class Refresh(val fromPull: Boolean) : LineDetailEvent
    data class SelectStop(val stop: BusStop) : LineDetailEvent
    data class FollowBus(val bus: LiveBus) : LineDetailEvent
    data object OpenBusList : LineDetailEvent
    data object BackToStationCard : LineDetailEvent
    data object SwitchDirection : LineDetailEvent
    data object ToggleFavorite : LineDetailEvent
    data object OpenGroupPicker : LineDetailEvent
    data object DismissGroupPicker : LineDetailEvent
    data class MoveToGroup(val groupId: String?) : LineDetailEvent
    data object OpenTimetable : LineDetailEvent
    data object DismissTimetable : LineDetailEvent
    data object OpenReminder : LineDetailEvent
    data object DismissReminder : LineDetailEvent
    data class SaveReminder(
        val hour: Int,
        val minute: Int,
        val daysOfWeek: Set<Int>,
        val autoStartTracking: Boolean
    ) : LineDetailEvent
    /** 底栏主按钮：追踪这班车 / 结束追踪 */
    data object ToggleTracking : LineDetailEvent
    /** 底栏乘车码：一点就开默认渠道，「每次询问」才弹选择 */
    data object OpenTransitCode : LineDetailEvent
    data object DismissTransitCodeSheet : LineDetailEvent
    data class PickTransitCode(val provider: TransitCodeProvider, val saveAsDefault: Boolean) : LineDetailEvent
    data class MessageShown(val id: Long) : LineDetailEvent
    /** 长辈版「读给我听」：朗读主卡上的到站 */
    data object ReadAloud : LineDetailEvent
}

/** 走向条之前的列表项数（方向头、主卡；有错误条时多一项） */
private fun railStartIndex(hasError: Boolean): Int = if (hasError) 3 else 2

/**
 * 线路详情（v3）：顶栏「返回 · 线路名 · 时钟 · ☆」；方向「始发 → 终点」+「换向」、运营信息 +「时刻表 ›」；
 * 主卡；竖向走向条；底栏「主按钮 + 乘车码」；下拉刷新；收藏等一次性提示走提示条。
 *
 * 底栏自己让开系统导航栏，列表底部留白 = Scaffold 的 innerPadding（含底栏和导航栏），三键导航下不重叠（issue #2）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetailScreen(
    state: LineDetailUiState,
    onEvent: (LineDetailEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val static = state.static

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LineTopBar(
                title = static?.line?.name ?: state.lineNameHint,
                source = static?.line?.source,
                isFavorite = state.isFavorite,
                actionsEnabled = static != null && state.selectedStop != null,
                onBack = { onEvent(LineDetailEvent.Back) },
                onReminder = { onEvent(LineDetailEvent.OpenReminder) },
                onToggleFavorite = { onEvent(LineDetailEvent.ToggleFavorite) }
            )
        },
        bottomBar = {
            if (static != null) {
                LineBottomBar(
                    isTracking = state.isTrackingThisStop,
                    canTrack = state.selectedStop != null && state.live != null,
                    onToggleTracking = { onEvent(LineDetailEvent.ToggleTracking) },
                    onOpenTransitCode = { onEvent(LineDetailEvent.OpenTransitCode) }
                )
            }
        },
        // 一次性提示：一次显示一条，显示完告诉 ViewModel
        snackbarHost = {
            UserMessageHost(
                message = state.userMessages.firstOrNull()?.let {
                    HostMessage(key = it.id, text = it.text, actionLabel = it.action?.label, duration = SnackbarDuration.Short)
                },
                onResult = { key, actionPerformed ->
                    val message = state.userMessages.firstOrNull { it.id == key }
                    if (actionPerformed && message?.action == LineUserMessage.Action.CHANGE_GROUP) {
                        currentOnEvent(LineDetailEvent.OpenGroupPicker)
                    }
                    currentOnEvent(LineDetailEvent.MessageShown(key as Long))
                }
            )
        }
    ) { innerPadding ->
        when {
            static == null && state.isLoading -> FullscreenLoadingView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = stringResource(LineRes.string.line_detail_loading)
            )

            static == null -> FullscreenErrorView(
                errorMessage = state.errorMessage ?: stringResource(LineRes.string.line_detail_error),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRetry = { onEvent(LineDetailEvent.Refresh(fromPull = false)) }
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isPullRefreshing,
                onRefresh = { onEvent(LineDetailEvent.Refresh(fromPull = true)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                val live = state.live
                val busesByOrder = remember(live?.buses) { live?.buses.orEmpty().groupBy { it.order } }
                val hasError = state.errorMessage != null
                LazyColumn(
                    state = listState,
                    // 底部 = 底栏 + 系统导航栏（Scaffold 已算进 innerPadding），不用 consumeWindowInsets，避免被吃成 0（issue #2）
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "direction", contentType = "direction") {
                        DirectionHeader(
                            static = static,
                            onSwitchDirection = { onEvent(LineDetailEvent.SwitchDirection) },
                            onOpenTimetable = { onEvent(LineDetailEvent.OpenTimetable) }
                        )
                    }
                    if (hasError) {
                        item(key = "error", contentType = "error") {
                            ErrorBanner(
                                text = state.errorMessage.orEmpty(),
                                onRetry = { onEvent(LineDetailEvent.Refresh(fromPull = false)) }
                            )
                        }
                    }
                    item(key = "hero", contentType = "hero") {
                        HeroCard(
                            state = state,
                            static = static,
                            onEvent = onEvent,
                            onPickStop = {
                                val order = state.selectedStop?.order ?: return@HeroCard
                                val index = railStartIndex(hasError) + routeRailItemIndex(static.stops, order)
                                scope.launch { listState.animateScrollToItem((index - 2).coerceAtLeast(0)) }
                            }
                        )
                    }
                    item(key = "rail_gap", contentType = "gap") { Spacer(modifier = Modifier.height(12.dp)) }
                    routeRailItems(
                        stops = static.stops,
                        busesByOrder = busesByOrder,
                        selectedOrder = state.selectedStop?.order,
                        selectedBusId = state.followedBusId,
                        onSelectStop = { onEvent(LineDetailEvent.SelectStop(it)) },
                        onSelectBus = { onEvent(LineDetailEvent.FollowBus(it)) }
                    )
                }
            }
        }
    }

    // 时刻表
    val timetable = state.timetable
    if (state.isTimetableSheetOpen && timetable != null) {
        TimetableBottomSheet(
            timetable = timetable,
            line = static?.line,
            onDismiss = { onEvent(LineDetailEvent.DismissTimetable) }
        )
    }

    // 乘车码选择（只在「每次询问」时出现）
    if (state.isTransitCodeSheetOpen) {
        TransitCodeBottomSheet(
            onDismiss = { onEvent(LineDetailEvent.DismissTransitCodeSheet) },
            onSelectProvider = { provider, saveAsDefault ->
                onEvent(LineDetailEvent.PickTransitCode(provider, saveAsDefault))
            }
        )
    }

    // 定时提醒
    val selectedStop = state.selectedStop
    if (state.isReminderDialogOpen && static != null && selectedStop != null) {
        ScheduledReminderDialog(
            lineName = static.line.name,
            endStopName = static.line.endStop,
            targetStopName = selectedStop.name,
            onDismissRequest = { onEvent(LineDetailEvent.DismissReminder) },
            onConfirm = { hour, minute, daysOfWeek, autoStartTracking ->
                onEvent(LineDetailEvent.SaveReminder(hour, minute, daysOfWeek, autoStartTracking))
            }
        )
    }

    // 换分组
    if (state.isGroupPickerOpen) {
        FavoriteGroupPickerSheet(
            groups = state.favoriteGroups,
            currentGroupId = state.favoriteGroupId,
            onPick = { onEvent(LineDetailEvent.MoveToGroup(it)) },
            onDismiss = { onEvent(LineDetailEvent.DismissGroupPicker) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineTopBar(
    title: String,
    source: DataSourceType?,
    isFavorite: Boolean,
    actionsEnabled: Boolean,
    onBack: () -> Unit,
    onReminder: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (source != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DataSourceBadge(
                        style = when (source) {
                            DataSourceType.CHELAILE -> DataSourceBadgeStyle.CHELAILE
                            DataSourceType.MYGOLBS -> DataSourceBadgeStyle.MYGOLBS
                        },
                        text = source.displayName
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = vectorResource(Res.drawable.ic_back), contentDescription = stringResource(Res.string.action_back))
            }
        },
        actions = {
            IconButton(onClick = onReminder, enabled = actionsEnabled) {
                Icon(imageVector = vectorResource(Res.drawable.ic_clock), contentDescription = stringResource(LineRes.string.line_detail_reminder))
            }
            IconButton(onClick = onToggleFavorite, enabled = actionsEnabled) {
                Icon(
                    imageVector = vectorResource(if (isFavorite) Res.drawable.ic_star_filled else Res.drawable.ic_star),
                    contentDescription = if (isFavorite) stringResource(Res.string.action_unfavorite) else stringResource(Res.string.action_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** 「始发 → 终点  换向」+「运营 05:30–23:30 · 票价 2元 · 共 24 站 · 时刻表 ›」。只读静态部分，轮询时不重组 */
@Composable
private fun DirectionHeader(
    static: LineStatic,
    onSwitchDirection: () -> Unit,
    onOpenTimetable: () -> Unit
) {
    val line = static.line
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${line.startStop} → ${line.endStop}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            // 反向切换只在这里一处（v3「换向贴在起终点旁」）
            if (static.reverseDirection != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onSwitchDirection,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_swap),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(LineRes.string.line_detail_reverse), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = listOfNotNull(
                stringResource(LineRes.string.line_detail_hours, line.firstTime.ifEmpty { "—" }, line.lastTime.ifEmpty { "—" }),
                line.price.takeIf { it.isNotEmpty() }?.let { stringResource(LineRes.string.line_detail_fare, it) },
                stringResource(LineRes.string.line_detail_stop_count, static.stops.size)
            ).joinToString(" · ")
            Text(
                text = meta,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
            TextButton(onClick = onOpenTimetable) {
                Text(text = stringResource(LineRes.string.line_detail_timetable), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text(text = stringResource(Res.string.action_retry), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            }
        }
    }
}

/** 主卡：到站 / 全部车辆 / 单车跟随 三种内容切换 */
@Composable
private fun HeroCard(
    state: LineDetailUiState,
    static: LineStatic,
    onEvent: (LineDetailEvent) -> Unit,
    onPickStop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.busColors.stationCardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        AnimatedContent(
            targetState = state.panelMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 60)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 140)) using
                    SizeTransform(
                        clip = false,
                        sizeAnimationSpec = { _, _ ->
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        }
                    )
            },
            contentAlignment = Alignment.TopCenter,
            label = "panel_state_transition",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
        ) { mode ->
            when (mode) {
                PanelMode.STATION_CARD -> StationArrivalContent(
                    static = static,
                    live = state.live,
                    selectedStop = state.selectedStop,
                    isTrackingActive = state.isTrackingThisStop,
                    onPickStop = onPickStop,
                    onOpenBusList = { onEvent(LineDetailEvent.OpenBusList) }
                )

                PanelMode.BUS_LIST -> BusListContent(
                    lineDetail = rememberLineDetail(static, state.live),
                    selectedStop = state.selectedStop,
                    onBack = { onEvent(LineDetailEvent.BackToStationCard) },
                    onSelectBus = { onEvent(LineDetailEvent.FollowBus(it)) }
                )

                PanelMode.BUS_FOLLOW -> {
                    val detail = rememberLineDetail(static, state.live)
                    BusFollowContent(
                        lineDetail = detail,
                        followedBus = detail.buses.find { it.busId == state.followedBusId },
                        onBack = { onEvent(LineDetailEvent.BackToStationCard) }
                    )
                }
            }
        }
    }
}

/** 车辆列表 / 单车跟随还吃整份 [BusLineDetail]，由静态 + 实时拼回去 */
@Composable
private fun rememberLineDetail(static: LineStatic, live: LineLive?): BusLineDetail = remember(static, live) {
    BusLineDetail(
        line = static.line,
        reverseDirection = static.reverseDirection,
        stops = static.stops,
        buses = live?.buses.orEmpty(),
        operatingState = live?.operatingState ?: LineOperatingState.RUNNING,
        statusDescription = live?.statusDescription.orEmpty(),
        nextDepartureFromStart = live?.nextDepartureFromStart,
        updatedAtMillis = live?.updatedAtMillis ?: 0L
    )
}

/** 底栏：主按钮 + 乘车码（和首页悬浮按钮同一个位置）；自己让开系统导航栏 */
@Composable
private fun LineBottomBar(
    isTracking: Boolean,
    canTrack: Boolean,
    onToggleTracking: () -> Unit,
    onOpenTransitCode: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isTracking) {
                OutlinedButton(
                    onClick = onToggleTracking,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(text = stringResource(Res.string.action_end_tracking), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onToggleTracking,
                    enabled = canTrack,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(LineRes.string.line_detail_track_this), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            FilledTonalIconButton(
                onClick = onOpenTransitCode,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_qr_code),
                    contentDescription = stringResource(Res.string.transit_code),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
