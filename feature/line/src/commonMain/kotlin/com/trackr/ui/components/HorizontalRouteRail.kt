package com.trackr.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackr.core.designsystem.rail.drawHorizontalTrackSegment
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_bus
import com.trackr.core.designsystem.theme.busChipSpringSpec
import com.trackr.core.designsystem.theme.busColors
import com.trackr.core.model.BusStop
import com.trackr.core.model.LiveBus
import com.trackr.feature.line.resources.Res as LineRes
import com.trackr.feature.line.resources.line_rail_arrived
import com.trackr.feature.line.resources.line_rail_condition
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * 横排走向条（横排模式）：一条从左到右的轨道，站名竖排在线下方，车标按 [LiveBus.segmentProgress] 在站间走。
 *
 * 卡片高度按全线最长站名算，见 [horizontalRailCardHeight]；外面没有竖排站点列表。
 * 点整列 = 选这个站，点车标 = 跟随这辆车。
 */
val HorizontalRailColumnWidth = 56.dp
private val RailColumnWidth = HorizontalRailColumnWidth
private val RailSidePadding = 16.dp
private val RailBusLaneHeight = 36.dp
private val RailTrackHeight = 24.dp
private val RailChipWidth = 46.dp
private val RailChipHeight = 18.dp
private val RailNameLineHeight = 14.sp
private val RailCardVerticalPadding = 16.dp
private const val RailMaxNameLines = 8
private const val RailColumnWidthValue = 56f
private const val RailChipWidthValue = 46f

/**
 * 横排走向条的卡片高度：车标层 + 轨道层 + 竖排站名 + 条件站「响应」和留白 + 卡片上下内边距。
 *
 * @param charHeight 一行站名的实际高度（14.sp 按当前字体缩放换算成 dp）
 */
fun horizontalRailCardHeight(stops: List<BusStop>, charHeight: Dp): Dp {
    val chars = stops.maxOfOrNull { it.name.length }?.coerceIn(1, RailMaxNameLines) ?: 5
    return RailBusLaneHeight + RailTrackHeight + charHeight * chars + 3.dp + 18.dp + RailCardVerticalPadding
}

/** 按当前字体缩放算卡片高度，页面限高和组件本身都用这一个 */
@Composable
fun horizontalRailCardHeight(stops: List<BusStop>): Dp {
    val charHeight = with(LocalDensity.current) { RailNameLineHeight.toDp() }
    return horizontalRailCardHeight(stops, charHeight)
}

/**
 * 车在横条上的浮点站下标；null = 这辆车不在本站表里，不画。
 *
 * 用列表下标算，不用 order - 1：个别数据源的站序不连续、或有重复，按下标才不会画到线外面。
 * 没坐标（[LiveBus.hasValidProgress] 为 false）或已到站时贴目标站，不猜中间的进度。
 */
internal fun busRailPosition(stops: List<BusStop>, bus: LiveBus): Float? {
    val index = stops.indexOfFirst { it.order == bus.order }
    if (index < 0) return null
    if (bus.isArrived || !bus.hasValidProgress) return index.toFloat()
    if (index == 0) return 0f
    return (index - 1) + bus.segmentProgress.coerceIn(0f, 1f)
}

@Composable
fun HorizontalRouteRail(
    stops: List<BusStop>,
    buses: List<LiveBus>,
    selectedStop: BusStop?,
    selectedBusId: String?,
    scrollState: ScrollState,
    onSelectStop: (BusStop) -> Unit,
    onSelectBus: (LiveBus) -> Unit,
    modifier: Modifier = Modifier
) {
    if (stops.isEmpty()) return

    val cardHeight = horizontalRailCardHeight(stops)
    val stripHeight = cardHeight - RailCardVerticalPadding
    val contentWidth = RailSidePadding * 2 + RailColumnWidth * stops.size
    val selectedOrder = selectedStop?.order ?: 1
    val busColors = MaterialTheme.busColors
    val placements = remember(stops, buses) { placeBuses(stops, buses) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.height(cardHeight)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
                    .horizontalScroll(scrollState)
            ) {
                Box(Modifier.width(contentWidth).height(stripHeight)) {
                    Spacer(
                        Modifier
                            .matchParentSize()
                            .drawWithCache {
                                val arrowPath = Path()
                                val arrowStroke = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                                val trackStroke = 3.dp.toPx()
                                val arrowGap = 4.dp.toPx()
                                val arrowArm = 3.dp.toPx()
                                val arrowSpread = 3.5.dp.toPx()
                                onDrawBehind {
                                    val y = (RailBusLaneHeight + RailTrackHeight / 2).toPx()
                                    for (i in 0 until stops.size - 1) {
                                        val startX = (RailSidePadding + RailColumnWidth * i + RailColumnWidth / 2).toPx()
                                        val endX = (RailSidePadding + RailColumnWidth * (i + 1) + RailColumnWidth / 2).toPx()
                                        drawHorizontalTrackSegment(
                                            startX = startX,
                                            endX = endX,
                                            y = y,
                                            color = getRouteSegmentColor(
                                                fromOrder = stops[i].order,
                                                toOrder = stops[i + 1].order,
                                                currentSelectedOrder = selectedOrder,
                                                busColors = busColors
                                            ),
                                            strokeWidth = trackStroke,
                                            hasArrow = i % 3 == 0,
                                            arrowPath = arrowPath,
                                            arrowStroke = arrowStroke,
                                            arrowGap = arrowGap,
                                            arrowArm = arrowArm,
                                            arrowSpread = arrowSpread
                                        )
                                    }
                                }
                            }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = RailSidePadding)
                    ) {
                        stops.forEach { stop ->
                            RailStopColumn(
                                stop = stop,
                                isSelected = stop.order == selectedStop?.order,
                                isPassed = stop.order < selectedOrder,
                                onSelect = { onSelectStop(stop) },
                                modifier = Modifier
                                    .width(RailColumnWidth)
                                    .height(stripHeight)
                            )
                        }
                    }

                    placements.forEach { placement ->
                        key(placement.bus.busId) {
                            RailBusChip(
                                bus = placement.bus,
                                position = placement.position,
                                tier = placement.tier,
                                isSelected = placement.bus.busId == selectedBusId,
                                onClick = { onSelectBus(placement.bus) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 每列：车标层留白 + 轨道节点 + 竖排站名 + 条件站的「响应」 */
@Composable
private fun RailStopColumn(
    stop: BusStop,
    isSelected: Boolean,
    isPassed: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busColors = MaterialTheme.busColors
    val nameColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isPassed -> busColors.busPassedGray
        else -> MaterialTheme.colorScheme.onSurface
    }
    val isConditionStop = !stop.statusNote.isNullOrEmpty()

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = stop.name }
            .clickable { onSelect() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(RailBusLaneHeight))
        Box(Modifier.height(RailTrackHeight), contentAlignment = Alignment.Center) {
            StationNode(
                isSelected = isSelected,
                isPassed = isPassed,
                isConditionStop = isConditionStop,
                onClick = onSelect
            )
        }
        Text(
            text = stop.name.map { it.toString() }.joinToString("\n"),
            fontSize = 12.sp,
            lineHeight = RailNameLineHeight,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = nameColor,
            textAlign = TextAlign.Center,
            maxLines = RailMaxNameLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (isConditionStop) {
            Text(
                text = stringResource(LineRes.string.line_rail_condition),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = busColors.conditionStopBorder,
                maxLines = 1
            )
        }
    }
}

/** 车标：位置用 spring 平滑过去；两辆挤在 46dp 内时上下错开一层 */
@Composable
private fun RailBusChip(
    bus: LiveBus,
    position: Float,
    tier: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val busColors = MaterialTheme.busColors
    val animatedPosition by animateFloatAsState(
        targetValue = position,
        animationSpec = busChipSpringSpec(),
        label = "bus_${bus.busId}"
    )
    val arrived = bus.isArrived
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        arrived -> busColors.busArrivingAmber
        else -> busColors.busChipContainer
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        arrived -> Color.White
        else -> busColors.busChipContent
    }
    val shortPlate = remember(bus.plateOrFleetNo) { formatBusChipShortText(bus.plateOrFleetNo) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .offset {
                val centerPx = with(density) {
                    (RailSidePadding + RailColumnWidth * animatedPosition + RailColumnWidth / 2).toPx()
                }
                val halfChipPx = with(density) { RailChipWidth.toPx() } / 2f
                IntOffset(
                    x = (centerPx - halfChipPx).roundToInt(),
                    y = with(density) { (RailChipHeight * tier).toPx() }.roundToInt()
                )
            }
            .width(RailChipWidth)
            .height(RailChipHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_bus),
                contentDescription = null,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = if (arrived) stringResource(LineRes.string.line_rail_arrived) else shortPlate,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class RailBusPlacement(
    val bus: LiveBus,
    val position: Float,
    val tier: Int
)

/** 按位置排车，挤在 [RailChipWidthValue] 以内的车上下错开一层 */
private fun placeBuses(stops: List<BusStop>, buses: List<LiveBus>): List<RailBusPlacement> {
    val positioned = buses.mapNotNull { bus ->
        val position = busRailPosition(stops, bus) ?: return@mapNotNull null
        bus to position
    }.sortedBy { it.second }

    var lastX = Float.NEGATIVE_INFINITY
    var tier = 0
    return positioned.map { (bus, position) ->
        val x = position * RailColumnWidthValue
        tier = if (x - lastX < RailChipWidthValue) (1 - tier) else 0
        lastX = x
        RailBusPlacement(bus = bus, position = position, tier = tier)
    }
}
