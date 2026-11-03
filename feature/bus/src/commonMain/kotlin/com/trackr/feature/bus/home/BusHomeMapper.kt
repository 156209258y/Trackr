package com.trackr.feature.bus.home

import com.trackr.core.domain.arrival.ArrivalDisplay
import com.trackr.core.domain.arrival.ArrivalState
import com.trackr.core.domain.arrival.ArrivalText
import com.trackr.core.domain.arrival.ArrivalTone
import com.trackr.core.domain.math.RouteProjectionMath
import com.trackr.core.model.FavoriteItem
import com.trackr.core.model.FavoriteStationLine
import com.trackr.core.model.LegPhase
import com.trackr.core.model.NearbyStop
import com.trackr.core.model.NearbyStopLineStatus
import com.trackr.core.model.TrackingSession
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.WatchedLine
import com.trackr.ui.components.arrival.ArrivalUi

/**
 * 首页数据 → 扁平 UI 模型（纯函数，单测直接测）。
 *
 * 文案按 v3：路线方向「始发 → 终点」；首页卡片的方向只写「→ 终点」（始发站太长放不下）；
 * 没车时有下一班发车时间就写「下一班 07:20 · 从 X 发车」，
 * 数据源给了状态字就照写，都没有才写「暂无在途车辆」。
 */
internal object BusHomeMapper {

    /** 一起等卡片里大字之外直接显示几条线，多的折叠成「还有 N 条」（R6） */
    const val WATCH_LINES_SHOWN = 2

    /** 重新算 UI 模型；和 [old] 相等的行沿用旧实例，列表没变就沿用旧列表（§4.1，强跳过靠 ===） */
    fun derive(old: BusHomeUiState, new: BusHomeUiState): BusHomeUiState {
        val card = trackingCard(new.trackingSession).reuse(old.trackingCard)
        val rows = reuseList(old.favoriteRows, favoriteRows(new), FavoriteRowUi::key)
        val nearby = reuseList(old.nearbyCards, nearbyCards(new), NearbyStopUi::key)
        return new.copy(trackingCard = card, favoriteRows = rows, nearbyCards = nearby)
    }

    // ---- 追踪卡 ----

    fun trackingCard(session: TrackingSession?): TrackingCardUi? {
        val state = session?.state ?: return null
        if (!state.isActive) return null
        val open = LineTarget(
            cityId = state.cityId.ifBlank { session.leg.cityId },
            lineId = state.lineId.ifBlank { session.leg.lineId },
            targetOrder = state.targetOrder,
            stopId = state.targetStopId,
            source = session.leg.source,
            lineName = state.lineName
        )
        val watch = session.watchTarget
        val watched = session.watchedLines
        if (watch != null && watched.isNotEmpty()) {
            val first = watched.first()
            val firstDisplay = ArrivalText.format(watchedLineState(first))
            val choice = session.boardChoice
            val detail = if (choice != null) {
                choice.options.map { it.lineName }.distinct().joinToString("、") + " 都在站"
            } else {
                joinDot("${first.line.lineName} 先到", firstDisplay.detail)
            }
            val others = watched.drop(1).map { line ->
                WatchLineUi(
                    key = line.line.lineId,
                    lineName = line.line.lineName,
                    arrival = ArrivalUi.of(ArrivalText.format(watchedLineState(line)))
                )
            }
            return TrackingCardUi(
                title = joinDot("一起等 ${watched.size} 条", watch.stopName),
                arrival = ArrivalUi.of(firstDisplay),
                detail = detail,
                otherLines = others.take(WATCH_LINES_SHOWN),
                foldedLines = others.drop(WATCH_LINES_SHOWN),
                open = open,
                spoken = ArrivalText.spoken(
                    watchedLineState(first),
                    lineName = first.line.lineName,
                    stopName = watch.stopName,
                    towards = first.line.endStopName
                )
            )
        }

        val sessionState = ArrivalText.stateForSession(state)
        val display = ArrivalText.format(sessionState)
        val loadingDetail = if (state.stopsRemaining < 0) state.statusSummary.ifBlank { display.detail } else display.detail
        val group = session.group
        return when {
            watch != null -> TrackingCardUi(
                // 一起等上车后只追坐上的那辆，到终点结束
                title = joinDot("一起等 · 已上 ${state.lineName}", watch.stopName),
                arrival = ArrivalUi.of(display),
                detail = joinDot("坐到 ${state.targetStopName}", loadingDetail),
                open = open
            )
            group != null -> {
                val routeName = routeTitle(group)
                val legText = if (group.legs.size > 1) "第 ${state.currentLegIndex + 1} 程，共 ${group.legs.size} 程" else ""
                val phaseText = when (state.legPhase) {
                    LegPhase.ON_BOARD -> "已上 ${state.lineName} · 到 ${state.targetStopName}下车"
                    else -> "${state.lineName} · ${state.boardStopName.ifBlank { state.targetStopName }}上车"
                }
                TrackingCardUi(
                    title = joinDot("路线 $routeName", legText),
                    arrival = ArrivalUi.of(display),
                    detail = joinDot(phaseText, loadingDetail),
                    open = open,
                    // 车上（等下车）不读「快去上车」那套
                    spoken = if (state.legPhase == LegPhase.ON_BOARD) {
                        ""
                    } else {
                        ArrivalText.spoken(
                            sessionState,
                            lineName = state.lineName,
                            stopName = state.boardStopName.ifBlank { state.targetStopName },
                            towards = state.endStopName
                        )
                    }
                )
            }
            else -> TrackingCardUi(
                title = joinDot("追踪 ${state.lineName}", state.targetStopName),
                arrival = ArrivalUi.of(display),
                detail = loadingDetail,
                open = open,
                spoken = ArrivalText.spoken(
                    sessionState,
                    lineName = state.lineName,
                    stopName = state.targetStopName,
                    towards = state.endStopName
                )
            )
        }
    }

    /** 一起等里一条线此刻的到站状态 */
    fun watchedLineState(line: WatchedLine): ArrivalState = when {
        !line.hasData -> ArrivalState.NoService(line.statusText.ifBlank { "暂无数据" })
        line.busId == null -> ArrivalState.NoService(line.statusText.ifBlank { "暂无在途车辆" })
        line.isArrived -> ArrivalState.Arriving()
        else -> ArrivalState.OnTheWay(
            stopsAway = line.stopsRemaining.takeIf { it >= 0 },
            minutes = ArrivalText.minutesOf(line.etaSeconds)
        )
    }

    // ---- 收藏 ----

    fun favoriteRows(state: BusHomeUiState): List<FavoriteRowUi> {
        val groupId = state.selectedGroupId
        return state.favoriteItems
            .filter { groupId == null || it.groupId == groupId }
            .map { item ->
                when (item) {
                    is FavoriteItem.Stop -> stopRow(item, state.favoritesRealtime[item.stop.id])
                    is FavoriteItem.Route -> routeRow(item)
                }
            }
    }

    private fun stopRow(item: FavoriteItem.Stop, realtime: NearbyStop?): FavoriteRowUi.Stop {
        val fav = item.stop
        val status = realtime?.lines?.firstOrNull { it.line.lineId == fav.lineId } ?: realtime?.lines?.firstOrNull()
        val display = if (status == null) {
            ArrivalText.format(ArrivalState.Loading, lineName = fav.lineName, stopName = fav.targetStopName, towards = fav.endStop)
        } else {
            arrivalDisplayFor(
                status,
                nextDepartureIsFromStart = true,
                lineName = fav.lineName,
                stopName = fav.targetStopName,
                towards = fav.endStop
            )
        }
        return FavoriteRowUi.Stop(
            key = item.key,
            lineName = fav.lineName,
            boardText = "${fav.targetStopName}上车",
            direction = terminalText(fav.endStop, fav.startStop),
            arrival = ArrivalUi.of(display),
            open = lineTargetOf(fav),
            spoken = display.spoken
        )
    }

    private fun routeRow(item: FavoriteItem.Route): FavoriteRowUi.Route {
        val route = item.route
        val lines = route.legs.mapNotNull { it.primaryLine?.lineName?.takeIf(String::isNotBlank) }
        val transfers = route.legs.size - 1
        val linesText = joinDot(
            lines.joinToString(" › "),
            if (transfers > 0) "换乘 $transfers 次" else ""
        )
        return FavoriteRowUi.Route(
            key = item.key,
            routeId = route.id,
            title = routeTitle(route),
            linesText = linesText
        )
    }

    fun routeTitle(route: TransitGroup): String {
        val ends = directionText(route.originStopName, route.destinationStopName)
        return route.name.trim().ifBlank { ends }
    }

    fun lineTargetOf(fav: FavoriteStationLine): LineTarget = LineTarget(
        cityId = fav.cityId,
        lineId = fav.lineId,
        targetOrder = fav.targetOrder,
        stopId = fav.targetStopId,
        source = fav.source,
        lineName = fav.lineName
    )

    // ---- 附近 ----

    fun nearbyCards(state: BusHomeUiState): List<NearbyStopUi> {
        val cityId = state.currentCity.cityId
        return state.nearbyStops.map { stop ->
            NearbyStopUi(
                key = nearbyKey(stop),
                name = stop.name,
                meta = joinDot(
                    RouteProjectionMath.formatDistanceText(stop.distanceMeters),
                    if (stop.lines.isNotEmpty()) "${stop.lines.size} 条线" else "",
                    stop.subwayLineNames.firstOrNull().orEmpty()
                ),
                lines = stop.lines.mapIndexed { index, status ->
                    NearbyLineUi(
                        key = "${status.line.lineId}_${status.targetOrder}_$index",
                        lineName = status.line.name,
                        direction = terminalText(status.line.endStop, status.line.startStop),
                        arrival = arrivalFor(status, nextDepartureIsFromStart = false),
                        open = LineTarget(
                            cityId = cityId,
                            lineId = status.line.lineId,
                            targetOrder = status.targetOrder,
                            stopId = stop.stopId,
                            source = status.line.source,
                            lineName = status.line.name
                        )
                    )
                },
                open = StopTarget(
                    cityId = cityId,
                    stopId = stop.stopId,
                    stopName = stop.name,
                    physicalStId = stop.physicalStId.orEmpty(),
                    namesakeStId = stop.namesakeStId.orEmpty(),
                    lat = stop.wgsLat,
                    lng = stop.wgsLng
                )
            )
        }
    }

    /** 附近站的 Lazy key（和旧首页一样：数据源 + 站 id，没有 id 时用名字和坐标） */
    fun nearbyKey(stop: NearbyStop): String =
        "${stop.source.name}_${stop.stopId.ifBlank { "${stop.name}_${stop.wgsLat}_${stop.wgsLng}" }}"

    // ---- 到站文字 ----

    /**
     * 一条线在某站的到站文字。
     *
     * 有车：按最近那辆算（[ArrivalText]）。没车：
     * - [nextDepartureIsFromStart] 为 true 时（收藏走的是线路详情接口，`preArrivalTime` 是始发站下一班发车时间）
     *   写「下一班 07:20 · 从 X 发车」；
     * - 数据源给了状态字（「等待发车」「末班已过」「预计 07:31 到达」）就照写；
     * - 都没有写「暂无在途车辆」。
     */
    fun arrivalFor(status: NearbyStopLineStatus, nextDepartureIsFromStart: Boolean): ArrivalUi =
        ArrivalUi.of(arrivalDisplayFor(status, nextDepartureIsFromStart))

    /** 同 [arrivalFor]，带朗读整句（长辈版「读给我听」）；[lineName]、[stopName]、[towards] 只用于朗读 */
    fun arrivalDisplayFor(
        status: NearbyStopLineStatus,
        nextDepartureIsFromStart: Boolean,
        lineName: String = "",
        stopName: String = "",
        towards: String = ""
    ): ArrivalDisplay {
        fun format(state: ArrivalState) = ArrivalText.format(state, lineName = lineName, stopName = stopName, towards = towards)
        val bus = status.approachingBuses.firstOrNull()
        if (bus != null) {
            return format(ArrivalText.stateForBus(status.targetOrder, bus))
        }
        val next = status.preArrivalTime?.takeIf { nextDepartureIsFromStart && ArrivalText.parseClock(it) != null }
        if (next != null && !isEndedText(status.statusText)) {
            return format(
                ArrivalState.AllPassed(nextDeparture = next, startStop = status.line.startStop, lastTime = status.line.lastTime)
            )
        }
        val text = status.statusText.trim()
        if (text.isNotEmpty()) {
            val tone = if (isEndedText(text)) ArrivalTone.Warn else ArrivalTone.Mute
            // 数据源的状态字照写照读
            return ArrivalDisplay(
                value = text,
                unit = null,
                detail = "",
                short = text,
                tone = tone,
                spoken = format(ArrivalState.NoService(text)).spoken
            )
        }
        return format(ArrivalState.NoService("暂无在途车辆"))
    }

    private fun isEndedText(text: String): Boolean =
        END_WORDS.any { text.contains(it) }

    private val END_WORDS = listOf("末班已过", "停运", "不在运营", "未开通", "暂停")

    // ---- 小工具 ----

    /**
     * 首页卡片的方向只写「→ 终点」，不写始发站：掌上公交的始发站名很长，和到站文字挤一行放不下。
     * 没有终点时退回始发站。
     */
    fun terminalText(end: String, start: String = ""): String {
        val e = end.trim()
        return if (e.isNotEmpty()) "→ $e" else start.trim()
    }

    /** 「始发 → 终点」；没有始发站时只写「→ 终点」（路线标题用，首页卡片用 [terminalText]） */
    fun directionText(start: String, end: String): String {
        val s = start.trim()
        val e = end.trim()
        return when {
            s.isNotEmpty() && e.isNotEmpty() -> "$s → $e"
            e.isNotEmpty() -> "→ $e"
            else -> s
        }
    }

    /** 用「 · 」连起非空的几段 */
    fun joinDot(vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(" · ")

    private fun TrackingCardUi?.reuse(old: TrackingCardUi?): TrackingCardUi? =
        if (this != null && this == old) old else this

    private fun <T : Any> reuseList(old: List<T>, new: List<T>, key: (T) -> String): List<T> {
        if (old == new) return old
        val oldByKey = old.associateBy(key)
        return new.map { item -> oldByKey[key(item)]?.takeIf { it == item } ?: item }
    }
}
