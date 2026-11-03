package com.trackr.feature.bus.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.action_edit
import com.trackr.core.designsystem.resources.ic_chevron_right
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.designsystem.theme.item
import com.trackr.feature.bus.resources.Res as BusRes
import com.trackr.feature.bus.resources.bus_favorites_all
import com.trackr.feature.bus.resources.bus_favorites_empty
import com.trackr.feature.bus.resources.bus_favorites_group_empty
import com.trackr.feature.bus.resources.bus_favorites_title
import com.trackr.ui.components.arrival.ArrivalTrailing
import com.trackr.ui.components.arrival.LineBadge
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * 「收藏」：车和路线一个列表（§4.3：key = `stop_x` / `route_x`，contentType 两种）；
 * 有分组时上面一排 chips（全部 + 各分组），右上角「编辑」。
 */
internal fun LazyListScope.favoritesSection(state: BusHomeUiState, onEvent: (BusHomeEvent) -> Unit) {
    val hasAny = state.favoriteItems.isNotEmpty() || state.favoriteGroups.isNotEmpty()

    item(key = "fav_header", contentType = "section_header") {
        SectionHeader(
            title = stringResource(BusRes.string.bus_favorites_title),
            trailing = if (hasAny) {
                {
                    TextButton(
                        onClick = { onEvent(BusHomeEvent.EditFavorites) },
                        modifier = Modifier.testTag(BusHomeTags.EDIT_FAVORITES)
                    ) { Text(stringResource(Res.string.action_edit)) }
                }
            } else null
        )
    }

    if (state.favoriteGroups.isNotEmpty()) {
        item(key = "fav_chips", contentType = "fav_chips") {
            GroupChips(state = state, onSelect = { onEvent(BusHomeEvent.SelectGroup(it)) })
        }
    }

    if (state.favoriteRows.isEmpty()) {
        item(key = "fav_empty", contentType = "empty_hint") {
            EmptyHint(
                text = if (state.favoriteItems.isEmpty()) {
                    stringResource(BusRes.string.bus_favorites_empty)
                } else {
                    stringResource(BusRes.string.bus_favorites_group_empty)
                }
            )
        }
    } else {
        items(
            items = state.favoriteRows,
            key = { it.key },
            contentType = {
                when (it) {
                    is FavoriteRowUi.Stop -> "fav_stop"
                    is FavoriteRowUi.Route -> "fav_route"
                }
            }
        ) { row ->
            when (row) {
                is FavoriteRowUi.Stop -> FavoriteStopRow(row, onClick = { onEvent(BusHomeEvent.OpenLine(row.open)) })
                is FavoriteRowUi.Route -> FavoriteRouteRow(row, onClick = { onEvent(BusHomeEvent.OpenRoute(row.routeId)) })
            }
        }
    }
}

@Composable
private fun GroupChips(state: BusHomeUiState, onSelect: (String?) -> Unit) {
    val dimens = TrackrTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceS)
    ) {
        FilterChip(
            selected = state.selectedGroupId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(BusRes.string.bus_favorites_all)) },
            modifier = Modifier.testTag(BusHomeTags.groupChip(null))
        )
        state.favoriteGroups.forEach { group ->
            FilterChip(
                selected = state.selectedGroupId == group.id,
                onClick = { onSelect(group.id) },
                label = { Text(group.name) },
                modifier = Modifier.testTag(BusHomeTags.groupChip(group.id))
            )
        }
    }
}

/** 收藏的车：线路号 | 「科技园站上车」「→ 科技园」 | 到站 */
@Composable
private fun FavoriteStopRow(row: FavoriteRowUi.Stop, onClick: () -> Unit) {
    RowCard(onClick = onClick, testTag = BusHomeTags.favoriteRow(row.key)) {
        LineBadge(name = row.lineName)
        Spacer(modifier = Modifier.width(TrackrTheme.dimens.spaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.boardText, style = MaterialTheme.typography.item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = row.direction,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(TrackrTheme.dimens.spaceS))
        ArrivalTrailing(arrival = row.arrival)
    }
}

/** 收藏的路线：「家 → 公司」「325 › 19 · 换乘 1 次」 */
@Composable
private fun FavoriteRouteRow(row: FavoriteRowUi.Route, onClick: () -> Unit) {
    RowCard(onClick = onClick, testTag = BusHomeTags.favoriteRow(row.key)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.linesText.isNotBlank()) {
                Text(
                    text = row.linesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = vectorResource(Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一行一张卡（圆角 16，页面左右 12） */
@Composable
internal fun RowCard(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable RowScope.() -> Unit
) {
    val dimens = TrackrTheme.dimens
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.cardPadding, vertical = dimens.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** 空状态一句话（v3） */
@Composable
internal fun EmptyHint(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TrackrTheme.dimens.screenPadding + 4.dp, vertical = TrackrTheme.dimens.spaceS)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
