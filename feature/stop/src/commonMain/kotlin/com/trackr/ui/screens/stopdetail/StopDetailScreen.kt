package com.trackr.ui.screens.stopdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.domain.arrival.ArrivalDisplay
import com.trackr.core.domain.arrival.ArrivalTone
import com.trackr.ui.components.transitcode.TransitCodeChooserSheet
import com.trackr.ui.icons.TrackrIcons

/**
 * 站点详情（第 21 步，v3）：无状态，只读 [state]、只发 [StopDetailEvent]。三种样子：
 * 普通（线路列表 +「选几条一起等」）、选线（勾线 +「收藏 / 一起等这 N 条」）、本站一起等进行中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopDetailScreen(
    state: StopDetailUiState,
    onEvent: (StopDetailEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val watch = state.watch.takeIf { !state.selecting }

    // 选线模式里系统返回 = 退出选线
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.selecting,
        onBackCompleted = { onEvent(StopDetailEvent.CancelSelecting) }
    )

    Scaffold(
        modifier = modifier,
        topBar = { StopDetailTopBar(state = state, watch = watch, onEvent = onEvent) },
        bottomBar = { StopDetailBottomBar(state = state, watch = watch, onEvent = onEvent) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        )
        when {
            state.isLoading && state.stops.isEmpty() -> CenterMessage(contentPadding) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    "正在查站点的线路和来车…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.errorMessage != null && state.stops.isEmpty() -> CenterMessage(contentPadding) {
                Text(state.errorMessage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onEvent(StopDetailEvent.Retry) }) { Text("重试") }
            }

            else -> PullToRefreshBox(
                isRefreshing = state.isPullRefreshing,
                onRefresh = { onEvent(StopDetailEvent.PullRefresh) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (watch != null) {
                        watchItems(watch, onEvent)
                    } else {
                        lineItems(state, onEvent)
                    }
                }
            }
        }
    }

    state.boardChoice?.let { choice ->
        ChooseBoardedBusSheet(
            choice = choice,
            onChoose = { onEvent(StopDetailEvent.ChooseBoardedBus(it)) },
            onDismiss = { onEvent(StopDetailEvent.DismissBoardChoice) }
        )
    }
    if (state.isTransitCodeSheetOpen) {
        TransitCodeChooserSheet(
            onDismiss = { onEvent(StopDetailEvent.DismissTransitCodeSheet) },
            onSelect = { provider, save -> onEvent(StopDetailEvent.PickTransitCode(provider, save)) }
        )
    }
}

// ------------------------------------------------------------------ 顶栏 / 底栏

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopDetailTopBar(state: StopDetailUiState, watch: WatchTogetherUi?, onEvent: (StopDetailEvent) -> Unit) {
    val (title, subtitle) = when {
        state.selecting -> "已选 ${state.selectedLineKeys.size} 条" to
            listOf(state.stopName, state.selectedPlatformLabel).filter { it.isNotBlank() }.joinToString(" · ")
        watch != null -> state.stopName to "一起等 ${watch.lineCount} 条"
        else -> state.stopName to state.subtitle
    }
    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (state.selecting) {
                IconButton(onClick = { onEvent(StopDetailEvent.CancelSelecting) }) {
                    Icon(TrackrIcons.Close, contentDescription = "退出选线")
                }
            } else {
                IconButton(onClick = { onEvent(StopDetailEvent.Back) }) {
                    Icon(TrackrIcons.Back, contentDescription = "返回")
                }
            }
        }
    )
}

@Composable
private fun StopDetailBottomBar(state: StopDetailUiState, watch: WatchTogetherUi?, onEvent: (StopDetailEvent) -> Unit) {
    // 没有站点数据时不放按钮（选线、一起等都没得选）
    if (state.stops.isEmpty() && watch == null) return
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                state.selecting -> {
                    val count = state.selectedLineKeys.size
                    OutlinedButton(
                        onClick = { onEvent(StopDetailEvent.SaveSelectionAsRoute) },
                        enabled = count > 0,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(TrackrIcons.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("收藏")
                    }
                    Button(
                        onClick = { onEvent(StopDetailEvent.StartWatching) },
                        enabled = count > 0,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(TrackrIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("一起等这 $count 条")
                    }
                }

                watch != null -> {
                    if (!watch.isOnBoard) {
                        Button(
                            onClick = { onEvent(StopDetailEvent.ConfirmBoarded) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(TrackrIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("我上车了")
                        }
                    }
                    TextButton(
                        onClick = { onEvent(StopDetailEvent.StopWatching) },
                        modifier = Modifier
                            .then(if (watch.isOnBoard) Modifier.weight(1f) else Modifier)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("结束", color = MaterialTheme.colorScheme.error)
                    }
                    TransitCodeButton(onEvent)
                }

                else -> {
                    OutlinedButton(
                        onClick = { onEvent(StopDetailEvent.StartSelecting) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(TrackrIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("选几条一起等")
                    }
                    TransitCodeButton(onEvent)
                }
            }
        }
    }
}

/** 乘车码：一点就开（默认平台直接打开，「每次询问」才弹选择），不藏、不用长按 */
@Composable
private fun TransitCodeButton(onEvent: (StopDetailEvent) -> Unit) {
    FilledTonalIconButton(
        onClick = { onEvent(StopDetailEvent.OpenTransitCode) },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(TrackrIcons.Qr, contentDescription = "乘车码")
    }
}

// ------------------------------------------------------------------ 普通 / 选线：线路列表

private fun LazyListScope.lineItems(state: StopDetailUiState, onEvent: (StopDetailEvent) -> Unit) {
    if (state.stops.size > 1) {
        item(key = "platforms", contentType = "platforms") {
            PlatformChips(state.platformLabels, state.selectedStopIndex, onEvent)
        }
    }
    if (state.rows.isEmpty()) {
        item(key = "empty", contentType = "empty") {
            Text(
                "这个站台暂时没有实时线路",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
            )
        }
    } else {
        items(items = state.rows, key = { it.key }, contentType = { "line_row" }) { row ->
            StopLineRow(
                row = row,
                selecting = state.selecting,
                checked = row.key in state.selectedLineKeys,
                onEvent = onEvent
            )
        }
    }
    if (state.selecting) {
        item(key = "select_hint", contentType = "hint") {
            Hint("勾上的线会一起追踪，谁先到就提醒谁。想等几条就勾几条。")
        }
    } else {
        state.transferText?.let { text ->
            item(key = "transfer", contentType = "hint") { Hint(text) }
        }
    }
}

@Composable
private fun PlatformChips(labels: List<String>, selected: Int, onEvent: (StopDetailEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selected,
                onClick = { onEvent(StopDetailEvent.SelectPlatform(index)) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun StopLineRow(
    row: StopLineRowUi,
    selecting: Boolean,
    checked: Boolean,
    onEvent: (StopDetailEvent) -> Unit
) {
    val onClick: (() -> Unit)? = when {
        !row.selectable -> null
        selecting -> ({ onEvent(StopDetailEvent.ToggleLine(row.key)) })
        else -> ({ onEvent(StopDetailEvent.OpenLine(row)) })
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, end = if (selecting) 14.dp else 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = if (row.selectable) ({ onEvent(StopDetailEvent.ToggleLine(row.key)) }) else null,
                    enabled = row.selectable
                )
                Spacer(Modifier.width(4.dp))
            }
            LineBadge(row.lineName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (row.endStop.isNotBlank()) "→ ${row.endStop}" else row.lineName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (row.hours.isNotBlank() && !selecting) {
                    Text(
                        text = row.hours,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            ArrivalColumn(row.arrival)
            if (!selecting) {
                IconButton(
                    onClick = { onEvent(StopDetailEvent.ToggleFavorite(row.key)) },
                    enabled = row.lineId.isNotBlank()
                ) {
                    Icon(
                        imageVector = if (row.isFavorite) TrackrIcons.StarFilled else TrackrIcons.Star,
                        contentDescription = if (row.isFavorite) "取消收藏" else "收藏",
                        tint = if (row.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 一起等进行中

private fun LazyListScope.watchItems(watch: WatchTogetherUi, onEvent: (StopDetailEvent) -> Unit) {
    if (watch.isOnBoard) {
        item(key = "watch_boarded", contentType = "watch_hero") {
            HeroCard(kicker = "只追坐上的这辆", big = watch.boardedTitle, unit = null, detail = watch.boardedDetail)
        }
        return
    }
    val first = watch.lines.firstOrNull()
    if (first != null) {
        item(key = "watch_hero", contentType = "watch_hero") {
            HeroCard(
                kicker = "${first.lineName} 先到",
                big = first.arrival.value,
                unit = first.arrival.unit,
                detail = first.detail.ifBlank { first.arrival.detail }
            )
        }
    }
    items(items = watch.lines, key = { "watch_${it.lineId}" }, contentType = { "watch_line" }) { line ->
        WatchLineRow(line)
    }
    item(key = "watch_add", contentType = "watch_add") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(StopDetailEvent.AddLine) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(TrackrIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("再加一条", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
    }
    item(key = "watch_hint", contentType = "hint") {
        Hint("谁先到，谁排第一、放大显示。点「我上车了」就认定是此刻在站的那辆，其他线停止追踪，开始提醒下车。")
    }
}

@Composable
private fun HeroCard(kicker: String, big: String, unit: String?, detail: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(kicker, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(big, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WatchLineRow(line: WatchLineUi) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineBadge(line.lineName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (line.endStop.isNotBlank()) "→ ${line.endStop}" else line.lineName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (line.plate.isNotBlank()) {
                    Text(line.plate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ArrivalColumn(line.arrival)
        }
    }
}

// ------------------------------------------------------------------ 小件

/** 线路号徽标 */
@Composable
internal fun LineBadge(name: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.widthIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** 右侧到站大字：数字 + 小单位，下面一行小字；颜色按 [ArrivalTone] */
@Composable
private fun ArrivalColumn(arrival: ArrivalDisplay) {
    val color = toneColor(arrival.tone)
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            val numeric = arrival.unit != null
            Text(
                text = arrival.value,
                color = color,
                style = if (numeric) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                },
                maxLines = 1
            )
            val unit = arrival.unit
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(unit, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (arrival.detail.isNotBlank()) {
            Text(
                arrival.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (arrival.detailIsWarning) TrackrTheme.colors.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun toneColor(tone: ArrivalTone): Color = when (tone) {
    ArrivalTone.Go -> TrackrTheme.colors.go
    ArrivalTone.Warn -> TrackrTheme.colors.warn
    ArrivalTone.Mute -> MaterialTheme.colorScheme.onSurfaceVariant
    ArrivalTone.Normal -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun CenterMessage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
