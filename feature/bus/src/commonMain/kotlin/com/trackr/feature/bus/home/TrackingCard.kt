package com.trackr.feature.bus.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.action_end
import com.trackr.core.designsystem.theme.TrackrTheme
import com.trackr.core.designsystem.theme.eta
import com.trackr.feature.bus.resources.Res as BusRes
import com.trackr.feature.bus.resources.bus_tracking_more_lines
import com.trackr.ui.components.arrival.ArrivalTrailing
import com.trackr.ui.components.arrival.LineBadge
import com.trackr.ui.components.arrival.arrivalToneColor
import org.jetbrains.compose.resources.stringResource

/**
 * 追踪卡（v3）：最先到的车放大；一起等的其他线排在下面，多了折叠成「还有 N 条」。
 * 点卡片去线路页，「结束」结束追踪。
 */
@Composable
internal fun TrackingCard(
    card: TrackingCardUi,
    onOpen: () -> Unit,
    onStop: () -> Unit
) {
    val dimens = TrackrTheme.dimens
    var showFolded by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding)
            .testTag(BusHomeTags.TRACKING_CARD)
    ) {
        Column(modifier = Modifier.padding(start = dimens.cardPadding, end = dimens.spaceXs, top = dimens.spaceM, bottom = dimens.spaceM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onStop, modifier = Modifier.testTag(BusHomeTags.STOP_TRACKING)) {
                    Text(stringResource(Res.string.action_end), color = MaterialTheme.colorScheme.error)
                }
            }
            Column(modifier = Modifier.padding(end = dimens.spaceM)) {
                Text(
                    text = card.arrival.big,
                    style = MaterialTheme.typography.eta,
                    color = arrivalToneColor(card.arrival.tone),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.detail.isNotBlank()) {
                    Text(text = card.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                card.otherLines.forEach { line -> WatchLineRow(line) }
                if (card.foldedLines.isNotEmpty()) {
                    if (showFolded) {
                        card.foldedLines.forEach { line -> WatchLineRow(line) }
                    } else {
                        TextButton(
                            onClick = { showFolded = true },
                            modifier = Modifier.testTag(BusHomeTags.WATCH_MORE)
                        ) {
                            Text(stringResource(BusRes.string.bus_tracking_more_lines, card.foldedLines.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchLineRow(line: WatchLineUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TrackrTheme.dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineBadge(name = line.lineName)
        Spacer(modifier = Modifier.width(8.dp).weight(1f))
        ArrivalTrailing(arrival = line.arrival)
    }
}
