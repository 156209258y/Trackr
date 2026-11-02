package com.trackr.ui.screens.routeplan.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.ui.screens.favoritesgroup.components.legLineColor
import com.trackr.ui.screens.routeplan.PlanSegment

/** 分段条：步行灰、第 1 程 primary、第 2 程起 legSecondary，宽度按时长比例 */
@Composable
internal fun SegmentBar(segments: List<PlanSegment>, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(MaterialTheme.shapes.small)
            .testTag("scheme_segment_bar"),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        segments.forEach { segment ->
            val background = if (segment.isWalk) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                legLineColor(segment.legIndex)
            }
            Box(
                modifier = Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(background),
                contentAlignment = Alignment.Center
            ) {
                if (segment.label.isNotEmpty()) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (segment.legIndex == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}
