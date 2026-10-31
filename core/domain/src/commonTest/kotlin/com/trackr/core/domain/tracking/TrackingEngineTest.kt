package com.trackr.core.domain.tracking

import com.trackr.core.domain.math.CoordTransform
import com.trackr.core.domain.math.RouteProjectionMath
import com.trackr.core.model.BusLineDetail
import com.trackr.core.model.BusLineDirection
import com.trackr.core.model.BusStop
import com.trackr.core.model.CandidateLine
import com.trackr.core.model.DataSourceType
import com.trackr.core.model.LegPhase
import com.trackr.core.model.LineOperatingState
import com.trackr.core.model.LiveBus
import com.trackr.core.model.TrackingLegParams
import com.trackr.core.model.TrackingSession
import com.trackr.core.model.TrackingSessionState
import com.trackr.core.model.TrackingTarget
import com.trackr.core.model.TransitGroup
import com.trackr.core.model.TransitLeg
import com.trackr.core.model.UserFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 追踪器写进 updatedAtMillis 的「现在」（第 7 步起由调用方传入），这些用例不看它 */
private const val NOW_MILLIS = 1_000_000L

/**
 * 追踪状态机 [TrackingEngine]，以及它内部用的 [GroupLegTracker]、[SingleLegTracker]、[BoardingDetector]、[LocationCadence]。
 *
 * - `group_*`：原 GroupLegTrackerTest（路线语义：上车站 A = order 3，下车站 B = order 8）
 * - `single_*`：原 SingleLegTrackerTest（目标候车站 = order 10，提前 2 站提醒）
 * - `boarding_*`：原 BoardingDetectionTest（上车站 A = order 3，坐标 STOP_LAT/STOP_LNG；“A 以北 N 米”，纬度每 0.001° ≈ 111 米）
 * - `transfer_*`：原 androidApp TransferAutoSwitchManagerTest 的纯逻辑部分（轮询间隔、下车站判定）
 * - `engine_*`：引擎对外行为（开始 / 轮询 / 切程 / 用户操作 / 定位取舍）
 */
class TrackingEngineTest {

    private companion object {
        // 原 BoardingDetectionTest
        const val STOP_LAT = 31.2300
        const val STOP_LNG = 121.4700
        const val T0 = 1_000_000L

        // 原 TransferAutoSwitchManagerTest
        const val TRANSFER_NOW = 1_000_000_000L

        // 引擎用例：单调时钟“现在”
        const val ELAPSED_NOW = 5_000_000L
    }

    // ================================================================ 原 GroupLegTrackerTest

    private fun groupBus(id: String, order: Int, arrived: Boolean = false, progress: Float = 0.5f, eta: Int = 300) =
        LiveBus(
            busId = id,
            plateOrFleetNo = id,
            order = order,
            segmentProgress = progress,
            isArrived = arrived,
            etaSeconds = eta
        )

    private fun groupWaitingSession() = TrackingSessionState(
        isActive = true,
        legPhase = LegPhase.WAITING_BOARD,
        lineName = "156路",
        boardStopOrder = 3,
        boardStopName = "上车站A",
        targetOrder = 8,
        targetStopName = "下车站B"
    )

    @Test
    fun group_waitingBoard_ignoresBusThatAlreadyPassedBoardStop() {
        // 人在 A（order 3）等车，前方一辆车已过 A（order 5），不应被选中
        val session = groupWaitingSession()
        val buses = listOf(
            groupBus("passed", order = 5, progress = 0.3f), // 已过 A
            groupBus("coming", order = 2, progress = 0.4f)  // 还没到 A
        )
        val update = GroupLegTracker.processPoll(session, buses, nowMillis = NOW_MILLIS)
        // 选中的应是还没到 A 的那辆
        assertEquals("coming", update.state.currentBusId)
        assertFalse(update.reachedBoardStop)
        assertEquals(LegPhase.WAITING_BOARD, update.state.legPhase)
    }

    @Test
    fun group_waitingBoard_recordsWaitStartOrderForNotificationProgress() {
        // 选中车辆时记录它所在的站作为等车区间起点；同一辆车后续轮询保持不变
        val first = GroupLegTracker.processPoll(groupWaitingSession(), listOf(groupBus("coming", order = 2, progress = 0.4f)), nowMillis = NOW_MILLIS)
        assertEquals(1, first.state.startTrackingOrder)
        val second = GroupLegTracker.processPoll(first.state, listOf(groupBus("coming", order = 3, progress = 0.2f)), nowMillis = NOW_MILLIS)
        assertEquals(1, second.state.startTrackingOrder)
    }

    @Test
    fun group_waitingBoard_noEligibleBus_whenAllPassedBoardStop() {
        val session = groupWaitingSession()
        val buses = listOf(groupBus("passed", order = 6))
        val update = GroupLegTracker.processPoll(session, buses, nowMillis = NOW_MILLIS)
        assertNull(update.state.currentBusId)
        assertFalse(update.reachedBoardStop)
    }

    @Test
    fun group_waitingBoard_lockBusWhenArriveAtBoardStop() {
        // 车到达 A（order==3 且已停靠）-> 锁定该车，进入 ON_BOARD
        val session = groupWaitingSession()
        val buses = listOf(groupBus("busX", order = 3, arrived = true, progress = 1.0f, eta = 0))
        val update = GroupLegTracker.processPoll(session, buses, nowMillis = NOW_MILLIS)
        assertTrue(update.reachedBoardStop)
        assertEquals(LegPhase.ON_BOARD, update.state.legPhase)
        assertEquals("busX", update.state.lockedBusId)
    }

    @Test
    fun group_onBoard_tracksOnlyLockedBus_andReachesDropStop() {
        val onBoard = groupWaitingSession().copy(
            legPhase = LegPhase.ON_BOARD,
            lockedBusId = "busX"
        )
        // 另一辆非锁定车已到 B，不应触发切程
        val otherAtB = listOf(
            groupBus("other", order = 8, arrived = true, progress = 1.0f, eta = 0),
            groupBus("busX", order = 5, progress = 0.5f, eta = 300)
        )
        val update1 = GroupLegTracker.processPoll(onBoard, otherAtB, nowMillis = NOW_MILLIS)
        assertFalse(update1.reachedDropStop)
        assertEquals("busX", update1.state.currentBusId)

        // 锁定车 busX 到达 B -> 触发切程 / 完成
        val lockedAtB = listOf(groupBus("busX", order = 8, arrived = true, progress = 1.0f, eta = 0))
        val update2 = GroupLegTracker.processPoll(onBoard, lockedAtB, nowMillis = NOW_MILLIS)
        assertTrue(update2.reachedDropStop)
    }

    @Test
    fun group_onBoard_lockedBusDisappears_keepsStateAndSignalsLost() {
        val onBoard = groupWaitingSession().copy(
            legPhase = LegPhase.ON_BOARD,
            lockedBusId = "busX",
            stopsRemaining = 2,
            statusSummary = "距下车站B 还有 2 站"
        )
        // 锁定车从数据消失，只有别的车
        val update = GroupLegTracker.processPoll(onBoard, listOf(groupBus("other", order = 7)), nowMillis = NOW_MILLIS)
        assertFalse(update.reachedDropStop)
        assertTrue(update.state.isSignalLost)
        // 不换成别的车
        assertEquals("busX", update.state.lockedBusId)
    }

    // ---------------- 到站口径统一 ----------------

    @Test
    fun group_arrival_etaZeroMeansNoPrediction_notArrived() {
        // eta=0 表示无预测；坐标缺失时 progress 默认 1.0 也不可信
        val noPrediction = groupBus("b", order = 3, progress = 1.0f, eta = 0)
        assertFalse(GroupLegTracker.isArrivedAtOrder(3, noPrediction))
        assertTrue(GroupLegTracker.isArrivedAtOrder(3, noPrediction.copy(etaSeconds = 30)))
    }

    @Test
    fun group_arrival_progressTrustedOnlyWithValidCoordinates() {
        val noCoords = groupBus("b", order = 3, progress = 1.0f, eta = 300)
        assertFalse(noCoords.hasValidProgress)
        assertFalse(GroupLegTracker.isArrivedAtOrder(3, noCoords))

        val withCoords = noCoords.copy(wgsLat = 30.27, wgsLng = 120.15)
        assertTrue(withCoords.hasValidProgress)
        assertTrue(GroupLegTracker.isArrivedAtOrder(3, withCoords))
    }

    @Test
    fun group_waitingBoard_missingCoordinatesDefaultProgress_doesNotLockBus() {
        // 车在 A 前一段、坐标缺失（progress 默认 1.0），不应误判到站并锁车
        val update = GroupLegTracker.processPoll(groupWaitingSession(), listOf(groupBus("b", order = 3, progress = 1.0f, eta = 200)), nowMillis = NOW_MILLIS)
        assertFalse(update.reachedBoardStop)
        assertEquals(LegPhase.WAITING_BOARD, update.state.legPhase)
    }

    @Test
    fun group_onBoard_lockedBusPassedDropStop_countsAsArrived() {
        val onBoard = groupWaitingSession().copy(legPhase = LegPhase.ON_BOARD, lockedBusId = "busX")
        val update = GroupLegTracker.processPoll(onBoard, listOf(groupBus("busX", order = 9, progress = 0.2f, eta = -1)), nowMillis = NOW_MILLIS)
        assertTrue(update.reachedDropStop)
    }

    // ---------------- 手动“我已上车”选车 ----------------

    @Test
    fun group_pickBoardedBus_prefersBusWithinAToAPlusOneNearestToA() {
        val buses = listOf(
            groupBus("next", order = 2, progress = 0.5f),           // 下一班，还没到 A
            groupBus("justLeft", order = 4, progress = 0.1f).copy(wgsLat = 30.0, wgsLng = 120.0), // 刚离开 A
            groupBus("farAhead", order = 6, progress = 0.5f)        // 早就开走
        )
        assertEquals("justLeft", GroupLegTracker.pickBoardedBus(3, buses)?.busId)
    }

    @Test
    fun group_pickBoardedBus_busStoppedAtA_wins() {
        val buses = listOf(
            groupBus("atA", order = 3, arrived = true, progress = 1.0f),
            groupBus("justLeft", order = 4, progress = 0.3f).copy(wgsLat = 30.0, wgsLng = 120.0)
        )
        assertEquals("atA", GroupLegTracker.pickBoardedBus(3, buses)?.busId)
    }

    @Test
    fun group_pickBoardedBus_noneInRange_returnsNull() {
        val buses = listOf(groupBus("next", order = 2), groupBus("farAhead", order = 6))
        assertNull(GroupLegTracker.pickBoardedBus(3, buses))
    }

    @Test
    fun group_confirmBoarded_locksPickedBus_insteadOfWaitingCandidate() {
        // 等车阶段 currentBusId 已经变成下一班（order 2），用户实际坐的是刚离开 A 的车
        val waiting = groupWaitingSession().copy(currentBusId = "next")
        val buses = listOf(
            groupBus("next", order = 2, progress = 0.5f),
            groupBus("mine", order = 4, progress = 0.2f).copy(wgsLat = 30.0, wgsLng = 120.0)
        )
        val confirmed = GroupLegTracker.confirmBoarded(waiting, buses, nowMillis = NOW_MILLIS)
        assertEquals(LegPhase.ON_BOARD, confirmed.legPhase)
        assertEquals("mine", confirmed.lockedBusId)
        assertEquals(3, confirmed.startTrackingOrder)
        assertFalse(confirmed.hasTriggeredApproachingAlert)
        assertFalse(confirmed.hasTriggeredArrivedAlert)
    }

    @Test
    fun group_confirmBoarded_withoutBus_relocksOnNextPoll() {
        val confirmed = GroupLegTracker.confirmBoarded(groupWaitingSession(), emptyList(), nowMillis = NOW_MILLIS)
        assertEquals(LegPhase.ON_BOARD, confirmed.legPhase)
        assertNull(confirmed.lockedBusId)

        // 下一次轮询：按同样规则补锁
        val update = GroupLegTracker.processPoll(
            confirmed,
            listOf(groupBus("next", order = 2), groupBus("mine", order = 4, progress = 0.4f)),
            nowMillis = NOW_MILLIS
        )
        assertEquals("mine", update.state.lockedBusId)
        assertEquals("mine", update.state.currentBusId)
        assertFalse(update.state.isSignalLost)
        assertEquals(4, update.state.stopsRemaining)
    }

    @Test
    fun group_onBoard_nullLockedBus_noCandidate_keepsWaitingForLock() {
        val confirmed = GroupLegTracker.confirmBoarded(groupWaitingSession(), emptyList(), nowMillis = NOW_MILLIS)
        val update = GroupLegTracker.processPoll(confirmed, listOf(groupBus("farAhead", order = 7)), nowMillis = NOW_MILLIS)
        assertNull(update.state.lockedBusId)
        assertFalse(update.reachedDropStop)
    }

    // ---------------- 提醒编排 ----------------

    @Test
    fun group_planAlerts_boardArrival_firesBoardAlertThenResetsFlags_andSkipsDropApproaching() {
        // 这一程只有 2 站（A=3, B=5），阈值 2：旧逻辑会在上车同一轮立刻发“即将到下车站”
        val waiting = groupWaitingSession().copy(targetOrder = 5, alertThresholdStops = 2, hasTriggeredApproachingAlert = true)
        val update = GroupLegTracker.processPoll(waiting, listOf(groupBus("busX", order = 3, arrived = true, progress = 1.0f)), nowMillis = NOW_MILLIS)
        assertTrue(update.reachedBoardStop)

        val decision = GroupLegTracker.planAlerts(waiting, update)
        assertEquals(GroupLegTracker.AlertKind.BOARD_ARRIVED, decision.alert)
        assertEquals("上车站A", decision.alertState?.targetStopName)
        assertEquals(LegPhase.ON_BOARD, decision.state.legPhase)
        assertFalse(decision.state.hasTriggeredApproachingAlert)
        assertFalse(decision.state.hasTriggeredArrivedAlert)

        // 下一轮才做下车站临近判定
        val next = GroupLegTracker.processPoll(decision.state, listOf(groupBus("busX", order = 4, progress = 0.3f)), nowMillis = NOW_MILLIS)
        val nextDecision = GroupLegTracker.planAlerts(decision.state, next)
        assertEquals(GroupLegTracker.AlertKind.DROP_APPROACHING, nextDecision.alert)
        assertTrue(nextDecision.state.hasTriggeredApproachingAlert)
    }

    @Test
    fun group_planAlerts_boardApproaching_usesBoardStopName_once() {
        val waiting = groupWaitingSession()
        val update = GroupLegTracker.processPoll(waiting, listOf(groupBus("coming", order = 2, progress = 0.4f)), nowMillis = NOW_MILLIS)
        val decision = GroupLegTracker.planAlerts(waiting, update)
        assertEquals(GroupLegTracker.AlertKind.BOARD_APPROACHING, decision.alert)
        assertEquals("上车站A", decision.alertState?.targetStopName)

        val again = GroupLegTracker.processPoll(decision.state, listOf(groupBus("coming", order = 2, progress = 0.6f)), nowMillis = NOW_MILLIS)
        assertNull(GroupLegTracker.planAlerts(decision.state, again).alert)
    }

    @Test
    fun group_planAlerts_dropArrival_notLastLeg_noArrivedAlert_lastLeg_arrivedAlert() {
        val onBoard = groupWaitingSession().copy(
            legPhase = LegPhase.ON_BOARD,
            lockedBusId = "busX",
            currentLegIndex = 0,
            totalLegs = 2
        )
        val atB = listOf(groupBus("busX", order = 8, arrived = true, progress = 1.0f))
        val transfer = GroupLegTracker.planAlerts(onBoard, GroupLegTracker.processPoll(onBoard, atB, nowMillis = NOW_MILLIS))
        // 非最后一程：只由换乘引擎发换乘提醒
        assertNull(transfer.alert)
        assertTrue(transfer.state.hasTriggeredArrivedAlert)

        val lastLeg = onBoard.copy(currentLegIndex = 1)
        val finish = GroupLegTracker.planAlerts(lastLeg, GroupLegTracker.processPoll(lastLeg, atB, nowMillis = NOW_MILLIS))
        assertEquals(GroupLegTracker.AlertKind.DROP_ARRIVED, finish.alert)
    }

    @Test
    fun group_planAlerts_signalLost_noDropApproaching() {
        val onBoard = groupWaitingSession().copy(legPhase = LegPhase.ON_BOARD, lockedBusId = "busX", stopsRemaining = 1)
        val update = GroupLegTracker.processPoll(onBoard, emptyList(), nowMillis = NOW_MILLIS)
        assertTrue(update.state.isSignalLost)
        assertNull(GroupLegTracker.planAlerts(onBoard, update).alert)
    }

    // ================================================================ 原 SingleLegTrackerTest

    private fun singleBus(id: String, order: Int, arrived: Boolean = false, progress: Float = 0.5f, eta: Int = 300) =
        LiveBus(
            busId = id,
            plateOrFleetNo = id,
            order = order,
            segmentProgress = progress,
            isArrived = arrived,
            etaSeconds = eta
        )

    private fun singleSession() = TrackingSessionState(
        isActive = true,
        lineName = "49路",
        targetStopName = "上海体育馆",
        targetOrder = 10,
        startTrackingOrder = 9,
        alertThresholdStops = 2
    )

    @Test
    fun single_picksNearestBusNotPassedTarget() {
        val update = SingleLegTracker.processPoll(
            singleSession(),
            listOf(singleBus("passed", order = 11), singleBus("far", order = 3), singleBus("near", order = 6)),
            nowMillis = NOW_MILLIS
        )
        assertEquals("near", update.state.currentBusId)
        assertEquals(4, update.state.stopsRemaining)
        assertNull(update.alert)
    }

    @Test
    fun single_approachingThenArrived_eachAlertOnce() {
        val first = SingleLegTracker.processPoll(singleSession(), listOf(singleBus("b1", order = 8)), nowMillis = NOW_MILLIS)
        assertEquals(SingleLegTracker.AlertKind.APPROACHING, first.alert)
        assertTrue(first.state.hasTriggeredApproachingAlert)

        val second = SingleLegTracker.processPoll(first.state, listOf(singleBus("b1", order = 9)), nowMillis = NOW_MILLIS)
        assertNull(second.alert)

        val arrived = SingleLegTracker.processPoll(second.state, listOf(singleBus("b1", order = 10, arrived = true, progress = 1f)), nowMillis = NOW_MILLIS)
        assertEquals(SingleLegTracker.AlertKind.ARRIVED, arrived.alert)
        assertTrue(arrived.state.isBusArrivedAtStop)
        assertTrue(arrived.state.hasTriggeredArrivedAlert)

        val again = SingleLegTracker.processPoll(arrived.state, listOf(singleBus("b1", order = 10, arrived = true, progress = 1f)), nowMillis = NOW_MILLIS)
        assertNull(again.alert)
    }

    @Test
    fun single_etaZeroAndDefaultProgress_isNotArrival() {
        // 坐标缺失时 progress 默认 1.0、eta=0 无预测：只能算“即将进站”，不能发到站提醒
        val update = SingleLegTracker.processPoll(singleSession(), listOf(singleBus("b1", order = 10, progress = 1f, eta = 0)), nowMillis = NOW_MILLIS)
        assertFalse(update.state.isBusArrivedAtStop)
        assertEquals(SingleLegTracker.AlertKind.APPROACHING, update.alert)
    }

    @Test
    fun single_switchingBusAfterPassingTarget_resetsAlertFlags() {
        val arrived = SingleLegTracker.processPoll(singleSession(), listOf(singleBus("b1", order = 10, arrived = true, progress = 1f)), nowMillis = NOW_MILLIS)
        assertTrue(arrived.state.hasTriggeredArrivedAlert)

        // b1 开过目标站，b2 在 order 9：换车并重新提醒
        val switched = SingleLegTracker.processPoll(arrived.state, listOf(singleBus("b1", order = 11), singleBus("b2", order = 9)), nowMillis = NOW_MILLIS)
        assertEquals("b2", switched.state.currentBusId)
        assertEquals(SingleLegTracker.AlertKind.APPROACHING, switched.alert)
        assertTrue(switched.state.hasTriggeredApproachingAlert)
        assertFalse(switched.state.hasTriggeredArrivedAlert)
    }

    @Test
    fun single_trackedBusBrieflyMissing_keepsSameBusForTwoPolls() {
        val tracking = SingleLegTracker.processPoll(singleSession(), listOf(singleBus("b1", order = 5)), nowMillis = NOW_MILLIS).state
        assertEquals("b1", tracking.currentBusId)

        val miss1 = SingleLegTracker.processPoll(tracking, listOf(singleBus("b2", order = 7)), nowMillis = NOW_MILLIS)
        assertEquals("b1", miss1.state.currentBusId)
        assertEquals(1, miss1.state.missingBusPolls)
        assertNull(miss1.alert)

        val miss2 = SingleLegTracker.processPoll(miss1.state, listOf(singleBus("b2", order = 7)), nowMillis = NOW_MILLIS)
        assertEquals("b1", miss2.state.currentBusId)
        assertEquals(2, miss2.state.missingBusPolls)

        // 第三轮仍不见：换车
        val miss3 = SingleLegTracker.processPoll(miss2.state, listOf(singleBus("b2", order = 7)), nowMillis = NOW_MILLIS)
        assertEquals("b2", miss3.state.currentBusId)
        assertEquals(0, miss3.state.missingBusPolls)
    }

    @Test
    fun single_trackedBusReappears_resetsMissingCounter() {
        val tracking = SingleLegTracker.processPoll(singleSession(), listOf(singleBus("b1", order = 5)), nowMillis = NOW_MILLIS).state
        val miss1 = SingleLegTracker.processPoll(tracking, emptyList(), nowMillis = NOW_MILLIS)
        val back = SingleLegTracker.processPoll(miss1.state, listOf(singleBus("b1", order = 6)), nowMillis = NOW_MILLIS)
        assertEquals("b1", back.state.currentBusId)
        assertEquals(0, back.state.missingBusPolls)
    }

    @Test
    fun single_noBus_notRunningLine_showsOperatingState() {
        val update = SingleLegTracker.processPoll(
            singleSession(),
            emptyList(),
            operatingState = LineOperatingState.ENDED,
            statusDescription = "",
            nowMillis = NOW_MILLIS
        )
        assertNull(update.state.currentBusId)
        assertEquals(-1, update.state.stopsRemaining)
        assertEquals(LineOperatingState.ENDED.defaultLabel, update.state.statusSummary)
    }

    // ================================================================ 原 BoardingDetectionTest

    private fun northOfStop(meters: Int): Double = STOP_LAT + meters / 111_000.0

    private fun boardingBus(
        id: String,
        order: Int,
        metersNorth: Int? = null,
        arrived: Boolean = false,
        progress: Float = 0.5f,
        eta: Int = 300
    ) = LiveBus(
        busId = id,
        plateOrFleetNo = id,
        order = order,
        segmentProgress = progress,
        isArrived = arrived,
        wgsLat = metersNorth?.let { northOfStop(it) } ?: 0.0,
        wgsLng = if (metersNorth != null) STOP_LNG else 0.0,
        etaSeconds = eta
    )

    private fun input(userMetersNorth: Int?, now: Long = T0, accuracy: Float = 10f) = BoardingDetector.Input(
        user = userMetersNorth?.let { BoardingDetector.UserPosition(northOfStop(it), STOP_LNG, accuracy) },
        boardStopLat = STOP_LAT,
        boardStopLng = STOP_LNG,
        nowMillis = now
    )

    private fun boardingWaitingSession() = TrackingSessionState(
        isActive = true,
        legPhase = LegPhase.WAITING_BOARD,
        lineName = "156路",
        boardStopOrder = 3,
        boardStopName = "上车站A",
        targetOrder = 8,
        targetStopName = "下车站B",
        totalLegs = 2
    )

    /** 车 b1 到站、用户就在站台：进入待确认 */
    private fun candidateSession(): TrackingSessionState {
        val update = GroupLegTracker.processPoll(
            boardingWaitingSession(),
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            input(userMetersNorth = 0),
            nowMillis = NOW_MILLIS
        )
        return GroupLegTracker.planAlerts(boardingWaitingSession(), update).state
    }

    @Test
    fun boarding_busArrives_userAtStop_startsCandidateInsteadOfBoarding() {
        val update = GroupLegTracker.processPoll(
            boardingWaitingSession(),
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            input(userMetersNorth = 20),
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.WAITING_BOARD, update.state.legPhase)
        assertEquals("b1", update.state.boardingCandidateBusId)
        assertTrue(update.state.isAwaitingBoardingConfirmation)
        assertTrue(update.busArrivedAtBoard)
        assertFalse(update.reachedBoardStop)
        assertNull(update.state.lockedBusId)
    }

    @Test
    fun boarding_busArrives_withoutLocation_keepsOriginalBoardingBehavior() {
        val update = GroupLegTracker.processPoll(
            boardingWaitingSession(),
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            boarding = null,
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.ON_BOARD, update.state.legPhase)
        assertEquals("b1", update.state.lockedBusId)
        assertTrue(update.reachedBoardStop)
    }

    @Test
    fun boarding_candidate_userRidesAwayWithBus_confirmsAfterTwoPolls() {
        val start = candidateSession()

        val first = GroupLegTracker.processPoll(
            start,
            listOf(boardingBus("b1", order = 4, metersNorth = 150)),
            input(userMetersNorth = 150, now = T0 + 10_000),
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.WAITING_BOARD, first.state.legPhase)
        assertEquals(1, first.state.boardingConfirmStreak)

        val second = GroupLegTracker.processPoll(
            first.state,
            listOf(boardingBus("b1", order = 4, metersNorth = 320)),
            input(userMetersNorth = 310, now = T0 + 20_000),
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.ON_BOARD, second.state.legPhase)
        assertEquals("b1", second.state.lockedBusId)
        assertTrue(second.reachedBoardStop)
        assertNull(second.state.boardingCandidateBusId)
        assertEquals(4, second.state.stopsRemaining)
    }

    @Test
    fun boarding_candidate_userStaysAtStopWhileBusLeaves_marksMissedAndWaitsForNextBus() {
        val start = candidateSession()
        assertTrue(start.hasTriggeredArrivedAlert)

        val first = GroupLegTracker.processPoll(
            start,
            listOf(boardingBus("b1", order = 4, metersNorth = 350), boardingBus("b2", order = 1, metersNorth = -2000)),
            input(userMetersNorth = 5, now = T0 + 10_000),
            nowMillis = NOW_MILLIS
        )
        assertEquals(1, first.state.boardingMissStreak)
        assertEquals(LegPhase.WAITING_BOARD, first.state.legPhase)

        val second = GroupLegTracker.processPoll(
            first.state,
            listOf(boardingBus("b1", order = 5, metersNorth = 700), boardingBus("b2", order = 1, metersNorth = -1800)),
            input(userMetersNorth = 5, now = T0 + 20_000),
            nowMillis = NOW_MILLIS
        )
        assertTrue(second.missedBoarding)
        assertEquals(LegPhase.WAITING_BOARD, second.state.legPhase)
        assertNull(second.state.boardingCandidateBusId)
        assertEquals("b2", second.state.currentBusId)
        // 新的一班车可以重新提醒
        assertFalse(second.state.hasTriggeredArrivedAlert)
        assertFalse(second.state.hasTriggeredApproachingAlert)
    }

    @Test
    fun boarding_busArrives_userFarFromStop_doesNotBoardAndNextBusCanAlertAgain() {
        val arrived = GroupLegTracker.processPoll(
            boardingWaitingSession(),
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true), boardingBus("b2", order = 1, metersNorth = -2000)),
            input(userMetersNorth = 1200),
            nowMillis = NOW_MILLIS
        )
        assertTrue(arrived.busArrivedAtBoard)
        assertNull(arrived.state.boardingCandidateBusId)
        assertEquals(LegPhase.WAITING_BOARD, arrived.state.legPhase)
        val afterAlert = GroupLegTracker.planAlerts(boardingWaitingSession(), arrived)
        assertEquals(GroupLegTracker.AlertKind.BOARD_ARRIVED, afterAlert.alert)

        // b1 开走了，b2 离 A 只剩 1 站：应重新发“即将到站”
        val next = GroupLegTracker.processPoll(
            afterAlert.state,
            listOf(boardingBus("b1", order = 4, metersNorth = 400), boardingBus("b2", order = 2, metersNorth = -300)),
            input(userMetersNorth = 1000, now = T0 + 30_000),
            nowMillis = NOW_MILLIS
        )
        assertEquals("b2", next.state.currentBusId)
        val nextAlert = GroupLegTracker.planAlerts(afterAlert.state, next)
        assertEquals(GroupLegTracker.AlertKind.BOARD_APPROACHING, nextAlert.alert)
    }

    @Test
    fun boarding_candidate_timeoutWithoutFix_boardsOnlyAfterBusLeftStop() {
        val start = candidateSession()

        val stillAtStop = GroupLegTracker.processPoll(
            start,
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            input(userMetersNorth = null, now = T0 + BoardingDetector.CANDIDATE_TIMEOUT_MILLIS + 1),
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.WAITING_BOARD, stillAtStop.state.legPhase)

        val left = GroupLegTracker.processPoll(
            stillAtStop.state,
            listOf(boardingBus("b1", order = 5, metersNorth = 900)),
            input(userMetersNorth = null, now = T0 + BoardingDetector.CANDIDATE_TIMEOUT_MILLIS + 10_000),
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.ON_BOARD, left.state.legPhase)
        assertEquals("b1", left.state.lockedBusId)
    }

    @Test
    fun boarding_candidate_locationBecomesUnavailable_fallsBackToBoarding() {
        val update = GroupLegTracker.processPoll(
            candidateSession(),
            listOf(boardingBus("b1", order = 4, metersNorth = 200)),
            boarding = null,
            nowMillis = NOW_MILLIS
        )
        assertEquals(LegPhase.ON_BOARD, update.state.legPhase)
        assertEquals("b1", update.state.lockedBusId)
    }

    @Test
    fun boarding_planAlerts_boardArrivedAlertFiresOnceAcrossCandidateAndConfirmation() {
        val waiting = boardingWaitingSession()
        val arrival = GroupLegTracker.processPoll(
            waiting,
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            input(userMetersNorth = 0),
            nowMillis = NOW_MILLIS
        )
        val first = GroupLegTracker.planAlerts(waiting, arrival)
        assertEquals(GroupLegTracker.AlertKind.BOARD_ARRIVED, first.alert)

        val pending = GroupLegTracker.processPoll(
            first.state,
            listOf(boardingBus("b1", order = 3, metersNorth = 0, arrived = true)),
            input(userMetersNorth = 0, now = T0 + 5_000),
            nowMillis = NOW_MILLIS
        )
        assertNull(GroupLegTracker.planAlerts(first.state, pending).alert)

        val c1 = GroupLegTracker.processPoll(
            pending.state,
            listOf(boardingBus("b1", order = 4, metersNorth = 150)),
            input(userMetersNorth = 150, now = T0 + 10_000),
            nowMillis = NOW_MILLIS
        )
        val c2 = GroupLegTracker.processPoll(
            c1.state,
            listOf(boardingBus("b1", order = 4, metersNorth = 300)),
            input(userMetersNorth = 300, now = T0 + 15_000),
            nowMillis = NOW_MILLIS
        )
        val boarded = GroupLegTracker.planAlerts(c1.state, c2)
        assertNull(boarded.alert)
        assertEquals(LegPhase.ON_BOARD, boarded.state.legPhase)
        // 进入 ON_BOARD 后下车站提醒标志重置
        assertFalse(boarded.state.hasTriggeredArrivedAlert)
        assertFalse(boarded.state.hasTriggeredApproachingAlert)
    }

    @Test
    fun boarding_onBoard_wrongLockedBus_relocksAfterThreeConsistentPolls() {
        var session = boardingWaitingSession().copy(
            legPhase = LegPhase.ON_BOARD,
            lockedBusId = "wrong",
            currentBusId = "wrong"
        )
        val buses = listOf(
            boardingBus("wrong", order = 5, metersNorth = 3000),
            boardingBus("right", order = 5, metersNorth = 1520)
        )
        repeat(2) { i ->
            val update = GroupLegTracker.processPoll(session, buses, input(userMetersNorth = 1500, now = T0 + i * 10_000L), nowMillis = NOW_MILLIS)
            assertFalse(update.relocked)
            assertEquals("wrong", update.state.lockedBusId)
            session = update.state
        }
        val third = GroupLegTracker.processPoll(session, buses, input(userMetersNorth = 1500, now = T0 + 30_000), nowMillis = NOW_MILLIS)
        assertTrue(third.relocked)
        assertEquals("right", third.state.lockedBusId)
        assertEquals("right", third.state.currentBusId)
    }

    @Test
    fun boarding_onBoard_lockedBusNearUser_neverRelocks() {
        var session = boardingWaitingSession().copy(legPhase = LegPhase.ON_BOARD, lockedBusId = "mine")
        val buses = listOf(boardingBus("mine", order = 5, metersNorth = 1500), boardingBus("other", order = 5, metersNorth = 1510))
        repeat(5) { i ->
            val update = GroupLegTracker.processPoll(session, buses, input(userMetersNorth = 1505, now = T0 + i * 10_000L), nowMillis = NOW_MILLIS)
            assertFalse(update.relocked)
            session = update.state
        }
        assertEquals("mine", session.lockedBusId)
    }

    @Test
    fun boarding_confirmBoarded_prefersBusAwaitingConfirmation() {
        val candidate = candidateSession()
        val buses = listOf(
            boardingBus("b1", order = 4, metersNorth = 200),
            boardingBus("closer", order = 3, metersNorth = -10, progress = 0.99f)
        )
        val confirmed = GroupLegTracker.confirmBoarded(candidate, buses, nowMillis = NOW_MILLIS)
        assertEquals(LegPhase.ON_BOARD, confirmed.legPhase)
        assertEquals("b1", confirmed.lockedBusId)
        assertNull(confirmed.boardingCandidateBusId)
    }

    @Test
    fun boarding_shouldStartCandidate_respectsRadius() {
        assertTrue(BoardingDetector.shouldStartCandidate(input(userMetersNorth = BoardingDetector.CANDIDATE_RADIUS_METERS - 20)))
        assertFalse(BoardingDetector.shouldStartCandidate(input(userMetersNorth = BoardingDetector.CANDIDATE_RADIUS_METERS + 50)))
        // 暂无定位：先进入待确认
        assertTrue(BoardingDetector.shouldStartCandidate(input(userMetersNorth = null)))
    }

    @Test
    fun boarding_locationCadence_followsTrackingPhase() {
        val waiting = boardingWaitingSession()
        assertEquals(LocationCadence.Tier.OFF, LocationCadence.tierFor(waiting.copy(stopsRemaining = 6, etaSeconds = 900)))
        assertEquals(LocationCadence.Tier.WAITING_NEAR, LocationCadence.tierFor(waiting.copy(stopsRemaining = 2)))
        assertEquals(LocationCadence.Tier.WAITING_NEAR, LocationCadence.tierFor(waiting.copy(stopsRemaining = 5, etaSeconds = 120)))
        assertEquals(LocationCadence.Tier.BOARDING_CHECK, LocationCadence.tierFor(waiting.copy(boardingCandidateBusId = "b1")))
        val riding = waiting.copy(legPhase = LegPhase.ON_BOARD, lockedBusId = "b1")
        assertEquals(LocationCadence.Tier.RIDING, LocationCadence.tierFor(riding.copy(stopsRemaining = 5)))
        assertEquals(LocationCadence.Tier.NEAR_DROP, LocationCadence.tierFor(riding.copy(stopsRemaining = 1)))
        assertEquals(LocationCadence.Tier.OFF, LocationCadence.tierFor(waiting.copy(legPhase = LegPhase.SINGLE)))
        assertEquals(LocationCadence.Tier.OFF, LocationCadence.tierFor(waiting.copy(isActive = false)))
    }

    // ================================================================ 原 TransferAutoSwitchManagerTest（纯逻辑部分）

    private fun transferFix(lat: Double, lng: Double, accuracy: Float? = 10f, ageSeconds: Long = 5) =
        UserFix(lat, lng, accuracy, TRANSFER_NOW - ageSeconds * 1_000L)

    private fun transferLegAtUser(): Pair<TransitLeg, Pair<Double, Double>> {
        val userWgsLat = 30.2740
        val userWgsLng = 120.1550
        val (targetGcjLat, targetGcjLng) = CoordTransform.wgs84ToGcj02(userWgsLat, userWgsLng)
        val leg = TransitLeg(
            legIndex = 0,
            fromStopName = "A",
            toStopName = "B",
            toStopLat = targetGcjLat,
            toStopLng = targetGcjLng
        )
        return leg to (userWgsLat to userWgsLng)
    }

    @Test
    fun transfer_adaptivePollingIntervalBackoff() {
        val approachingSession1 = TrackingSessionState(stopsRemaining = 1, etaSeconds = 120)
        assertEquals(10_000L, TrackingEngine.pollingIntervalMillis(approachingSession1))

        val approachingSession0 = TrackingSessionState(stopsRemaining = 0, etaSeconds = 30)
        assertEquals(10_000L, TrackingEngine.pollingIntervalMillis(approachingSession0))

        val mediumSession = TrackingSessionState(stopsRemaining = 3, etaSeconds = 450)
        assertEquals(20_000L, TrackingEngine.pollingIntervalMillis(mediumSession))

        val farSession = TrackingSessionState(stopsRemaining = 7, etaSeconds = 1200)
        assertEquals(45_000L, TrackingEngine.pollingIntervalMillis(farSession))

        val unknownSession = TrackingSessionState(stopsRemaining = -1, etaSeconds = -1)
        assertEquals(45_000L, TrackingEngine.pollingIntervalMillis(unknownSession))
    }

    @Test
    fun transfer_waitingBoardPhase_neverTriggersDropArrival() {
        // 等车阶段，即便 stopsRemaining==0 且公交到站，也绝不触发下车站到达 / 切程
        val leg = TransitLeg(
            legIndex = 0,
            fromStopName = "A",
            toStopName = "B",
            toStopLat = 30.2740,
            toStopLng = 120.1550
        )
        val waiting = TrackingSessionState(
            legPhase = LegPhase.WAITING_BOARD,
            stopsRemaining = 0,
            isBusArrivedAtStop = true
        )
        assertFalse(
            TrackingEngine.isArrivalSatisfied(
                state = waiting,
                leg = leg,
                userFix = transferFix(30.2740, 120.1550),
                nowElapsedMillis = TRANSFER_NOW
            )
        )
    }

    @Test
    fun transfer_onBoardPhase_busEventTriggersDropArrival() {
        val leg = TransitLeg(legIndex = 0, fromStopName = "A", toStopName = "B")
        val onBoard = TrackingSessionState(
            legPhase = LegPhase.ON_BOARD,
            stopsRemaining = 0,
            isBusArrivedAtStop = true
        )
        // 原用例走默认参数（setup 后没有定位）：这里显式传 null
        assertTrue(TrackingEngine.isArrivalSatisfied(onBoard, leg, userFix = null, nowElapsedMillis = TRANSFER_NOW))
    }

    @Test
    fun transfer_onBoardPhase_locationAssistTriggersDropArrival() {
        // ON_BOARD 阶段：公交未到站，但设备定位（WGS-84）距下车站（GCJ-02）< 150m，辅助判定到达。
        // leg 坐标是 GCJ-02；用户坐标是 WGS-84，判定时会 wgs84ToGcj02 转换对齐。
        val userWgsLat = 30.2740
        val userWgsLng = 120.1550
        val (targetGcjLat, targetGcjLng) = CoordTransform.wgs84ToGcj02(userWgsLat, userWgsLng)
        val leg = TransitLeg(
            legIndex = 0,
            fromStopName = "A",
            toStopName = "B",
            toStopLat = targetGcjLat,
            toStopLng = targetGcjLng
        )
        val onBoard = TrackingSessionState(
            legPhase = LegPhase.ON_BOARD,
            stopsRemaining = 2,
            isBusArrivedAtStop = false
        )
        // 用户定位转换后恰好落在目标站附近，< 150m
        assertTrue(
            TrackingEngine.isArrivalSatisfied(
                state = onBoard,
                leg = leg,
                userFix = transferFix(userWgsLat, userWgsLng),
                nowElapsedMillis = TRANSFER_NOW
            )
        )

        // 距离很远 -> false
        assertFalse(
            TrackingEngine.isArrivalSatisfied(
                state = onBoard,
                leg = leg,
                userFix = transferFix(30.3900, 120.2700),
                nowElapsedMillis = TRANSFER_NOW
            )
        )
    }

    @Test
    fun transfer_onBoardPhase_staleOrInaccurateFix_doesNotTriggerDropArrival() {
        val (leg, user) = transferLegAtUser()
        val onBoard = TrackingSessionState(legPhase = LegPhase.ON_BOARD, stopsRemaining = 2)

        // 定位超过 60 秒
        assertFalse(
            TrackingEngine.isArrivalSatisfied(onBoard, leg, transferFix(user.first, user.second, ageSeconds = 61), TRANSFER_NOW)
        )
        // 精度差于 50m
        assertFalse(
            TrackingEngine.isArrivalSatisfied(onBoard, leg, transferFix(user.first, user.second, accuracy = 80f), TRANSFER_NOW)
        )
        // 精度未知
        assertFalse(
            TrackingEngine.isArrivalSatisfied(onBoard, leg, transferFix(user.first, user.second, accuracy = null), TRANSFER_NOW)
        )
        // 边界：60 秒、50m 仍可用
        assertTrue(
            TrackingEngine.isArrivalSatisfied(
                onBoard, leg, transferFix(user.first, user.second, accuracy = 50f, ageSeconds = 60), TRANSFER_NOW
            )
        )
    }

    @Test
    fun transfer_onBoardPhase_etaZeroIsNotArrival() {
        // etaSeconds<=0 表示无预测，不能当成到站
        val leg = TransitLeg(legIndex = 0, fromStopName = "A", toStopName = "B")
        val onBoard = TrackingSessionState(legPhase = LegPhase.ON_BOARD, stopsRemaining = 0, etaSeconds = 0)
        assertFalse(TrackingEngine.isArrivalSatisfied(onBoard, leg, userFix = null, nowElapsedMillis = TRANSFER_NOW))
        assertTrue(TrackingEngine.isArrivalSatisfied(onBoard.copy(etaSeconds = 30), leg, userFix = null, nowElapsedMillis = TRANSFER_NOW))
    }

    @Test
    fun transfer_pollingInterval_backsOffOnFailures_capsAt120s_andResetsOnSuccess() {
        val far = TrackingSessionState(stopsRemaining = 7, etaSeconds = 1200)
        assertEquals(90_000L, TrackingEngine.pollingIntervalMillis(far, consecutiveFailures = 1))
        assertEquals(120_000L, TrackingEngine.pollingIntervalMillis(far, consecutiveFailures = 2))
        assertEquals(120_000L, TrackingEngine.pollingIntervalMillis(far, consecutiveFailures = 30))

        val near = TrackingSessionState(stopsRemaining = 1, etaSeconds = 120)
        assertEquals(20_000L, TrackingEngine.pollingIntervalMillis(near, consecutiveFailures = 1))
        assertEquals(40_000L, TrackingEngine.pollingIntervalMillis(near, consecutiveFailures = 2))
        // 成功后（失败次数清零）恢复正常间隔
        assertEquals(10_000L, TrackingEngine.pollingIntervalMillis(near, consecutiveFailures = 0))
    }

    @Test
    fun transfer_pollingInterval_notRunningLine_pollsEveryFiveMinutes() {
        val session = TrackingSessionState(stopsRemaining = -1, etaSeconds = -1)
        assertEquals(
            300_000L,
            TrackingEngine.pollingIntervalMillis(session, operatingState = LineOperatingState.ENDED)
        )
        assertEquals(
            300_000L,
            TrackingEngine.pollingIntervalMillis(session, operatingState = LineOperatingState.WAITING)
        )
    }

    @Test
    fun transfer_pollingInterval_signalLost_atLeast30s() {
        val lost = TrackingSessionState(stopsRemaining = 1, etaSeconds = 60, isSignalLost = true)
        assertEquals(30_000L, TrackingEngine.pollingIntervalMillis(lost))
        val lostFar = TrackingSessionState(stopsRemaining = 7, isSignalLost = true)
        assertEquals(45_000L, TrackingEngine.pollingIntervalMillis(lostFar))
    }

    @Test
    fun transfer_isUserFixUsable_requiresFreshAndAccurateFix() {
        val (_, user) = transferLegAtUser()
        assertFalse(TrackingEngine.isUserFixUsable(null, TRANSFER_NOW))
        assertTrue(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second), TRANSFER_NOW))
        // 边界：60 秒、50m 仍可用
        assertTrue(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, accuracy = 50f, ageSeconds = 60), TRANSFER_NOW))
        assertFalse(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, ageSeconds = 61), TRANSFER_NOW))
        assertFalse(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, accuracy = 50.5f), TRANSFER_NOW))
        // 精度未知 / 精度为 0 都不用
        assertFalse(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, accuracy = null), TRANSFER_NOW))
        assertFalse(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, accuracy = 0f), TRANSFER_NOW))
        // 定位时刻在“现在”之后（时基不一致）：不用
        assertFalse(TrackingEngine.isUserFixUsable(transferFix(user.first, user.second, ageSeconds = -1), TRANSFER_NOW))
    }

    @Test
    fun transfer_onBoardPhase_userWgsIsConvertedToGcjBeforeComparingWithDropStop() {
        // 下车站坐标（GCJ-02）= 用户 WGS-84 坐标转 GCJ-02 后再往北 50 m
        val userWgsLat = 30.2740
        val userWgsLng = 120.1550
        val (userGcjLat, userGcjLng) = CoordTransform.wgs84ToGcj02(userWgsLat, userWgsLng)
        val dropGcjLat = userGcjLat + 50 / 111_000.0
        val dropGcjLng = userGcjLng
        val leg = TransitLeg(
            legIndex = 0,
            fromStopName = "A",
            toStopName = "B",
            toStopLat = dropGcjLat,
            toStopLng = dropGcjLng
        )
        // 前提：转换后 < 150 m；若直接拿 WGS 坐标和 GCJ 坐标比则 > 150 m
        val converted = assertNotNull(RouteProjectionMath.calculateDistanceMeters(userGcjLat, userGcjLng, dropGcjLat, dropGcjLng))
        val unconverted = assertNotNull(RouteProjectionMath.calculateDistanceMeters(userWgsLat, userWgsLng, dropGcjLat, dropGcjLng))
        assertTrue(converted < TrackingEngine.DROP_STOP_RADIUS_METERS, "转换后距离 $converted m")
        assertTrue(unconverted > TrackingEngine.DROP_STOP_RADIUS_METERS, "不转换的距离 $unconverted m")

        val onBoard = TrackingSessionState(legPhase = LegPhase.ON_BOARD, stopsRemaining = 2)
        assertTrue(
            TrackingEngine.isArrivalSatisfied(
                state = onBoard,
                leg = leg,
                userFix = transferFix(userWgsLat, userWgsLng),
                nowElapsedMillis = TRANSFER_NOW
            )
        )
    }

    // ================================================================ 引擎对外行为

    private val line1 = CandidateLine(
        cityId = "hz", lineId = "L1", lineName = "156路",
        fromStopOrder = 3, toStopOrder = 8, endStopName = "终点一"
    )
    private val line1b = CandidateLine(
        cityId = "hz", lineId = "L1b", lineName = "156B路",
        fromStopOrder = 4, toStopOrder = 9, endStopName = "终点一B"
    )
    private val line2 = CandidateLine(
        cityId = "hz", lineId = "L2", lineName = "28路",
        fromStopOrder = 2, toStopOrder = 6, endStopName = "终点二"
    )
    private val line2b = CandidateLine(
        cityId = "hz", lineId = "L2b", lineName = "K28路",
        fromStopOrder = 5, toStopOrder = 10, endStopName = "终点二B",
        source = DataSourceType.MYGOLBS, mygolbsCityCode = "0571", direction = 1
    )

    private val leg0 = TransitLeg(
        legIndex = 0,
        fromStopName = "上车站A",
        toStopName = "换乘站B",
        toStopId = "B",
        candidateLines = listOf(line1, line1b)
    )
    private val leg1 = TransitLeg(
        legIndex = 1,
        fromStopName = "换乘站B",
        toStopName = "终点站C",
        toStopId = "C",
        candidateLines = listOf(line2, line2b)
    )

    private val group = TransitGroup(id = "g1", name = "上班", legs = listOf(leg0, leg1), createdAt = 0L, updatedAt = 0L)

    private fun routeTarget(startLeg: Int = 0, selected: Map<Int, String> = emptyMap(), legs: List<TransitLeg> = group.legs) =
        TrackingTarget.Route(group = group.copy(legs = legs), startLeg = startLeg, selectedLineIds = selected)

    private fun singleTarget(targetOrder: Int = 10, threshold: Int = 2) = TrackingTarget.SingleLine(
        cityId = "sh",
        lineId = "L49",
        lineName = "49路",
        endStopName = "终点站",
        targetStopId = "S10",
        targetStopName = "上海体育馆",
        targetOrder = targetOrder,
        totalStops = 20,
        alertThresholdStops = threshold,
        source = DataSourceType.MYGOLBS,
        mygolbsCityCode = "021",
        direction = 1
    )

    private fun beginRoute(startLeg: Int = 0, selected: Map<Int, String> = emptyMap(), generation: Int = 3): TrackingSession =
        assertNotNull(TrackingEngine.begin("s1", generation, routeTarget(startLeg, selected), NOW_MILLIS))

    private fun beginSingle(targetOrder: Int = 10, threshold: Int = 2): TrackingSession =
        assertNotNull(TrackingEngine.begin("s1", 1, singleTarget(targetOrder, threshold), NOW_MILLIS))

    private fun TrackingSession.onBoard(lockedBusId: String): TrackingSession =
        copy(state = state.copy(legPhase = LegPhase.ON_BOARD, lockedBusId = lockedBusId))

    private fun detail(buses: List<LiveBus>, stops: List<BusStop> = emptyList()) = BusLineDetail(
        line = BusLineDirection(lineId = "L", name = "线", startStop = "起点", endStop = "终点"),
        stops = stops,
        buses = buses,
        updatedAtMillis = 0L
    )

    /** 上车站 A（order 3）带 WGS 坐标 */
    private val boardStopWithCoords = BusStop(stopId = "A", name = "上车站A", order = 3, wgsLat = STOP_LAT, wgsLng = STOP_LNG)

    private fun serviceFixNorth(meters: Int, ageMillis: Long = 5_000L, accuracy: Float? = 10f) =
        UserFix(northOfStop(meters), STOP_LNG, accuracy, ELAPSED_NOW - ageMillis)

    private fun poll(
        session: TrackingSession,
        detail: BusLineDetail,
        locationEnabled: Boolean = false,
        serviceFix: UserFix? = null,
        dropFix: UserFix? = null
    ) = TrackingEngine.reducePoll(session, detail, locationEnabled, serviceFix, dropFix, NOW_MILLIS + 1, ELAPSED_NOW)

    // ---------------- begin ----------------

    @Test
    fun engine_beginSingle_buildsSinglePhaseSessionWithLegParams() {
        val session = assertNotNull(TrackingEngine.begin("s1", 5, singleTarget(targetOrder = 10, threshold = 3), NOW_MILLIS))
        assertEquals("s1", session.sessionId)
        assertEquals(5, session.generation)
        assertFalse(session.interrupted)
        assertEquals(
            TrackingLegParams(
                cityId = "sh",
                lineId = "L49",
                lineName = "49路",
                targetStopId = "S10",
                targetOrder = 10,
                source = DataSourceType.MYGOLBS,
                mygolbsCityCode = "021",
                direction = 1
            ),
            session.leg
        )
        val state = session.state
        assertTrue(state.isActive)
        assertEquals(LegPhase.SINGLE, state.legPhase)
        assertEquals(9, state.startTrackingOrder)
        assertEquals(10, state.targetOrder)
        assertEquals(20, state.totalStops)
        assertEquals(3, state.alertThresholdStops)
        assertEquals("上海体育馆", state.targetStopName)
        assertEquals(NOW_MILLIS, state.updatedAtMillis)
        assertNull(state.groupId)
        assertNull(session.toGroupSessionState())
    }

    @Test
    fun engine_beginSingle_startTrackingOrderIsAtLeastOne() {
        assertEquals(1, beginSingle(targetOrder = 1).state.startTrackingOrder)
        assertEquals(1, beginSingle(targetOrder = 2).state.startTrackingOrder)
    }

    @Test
    fun engine_beginRoute_startsWaitingBoardOnFirstCandidateLine() {
        val session = beginRoute(generation = 3)
        assertEquals(3, session.generation)
        val state = session.state
        assertEquals(LegPhase.WAITING_BOARD, state.legPhase)
        assertEquals(0, state.currentLegIndex)
        assertEquals(2, state.totalLegs)
        assertEquals("g1", state.groupId)
        assertEquals("上班", state.groupName)
        assertEquals("L1", state.lineId)
        assertEquals("156路", state.lineName)
        assertEquals(3, state.boardStopOrder)
        assertEquals("上车站A", state.boardStopName)
        assertEquals(3, state.startTrackingOrder)
        assertEquals(8, state.targetOrder)
        assertEquals("换乘站B", state.targetStopName)
        assertEquals("B", state.targetStopId)
        assertEquals(2, state.alertThresholdStops)
        assertEquals(TrackingLegParams(cityId = "hz", lineId = "L1", lineName = "156路", targetStopId = "B", targetOrder = 8), session.leg)
    }

    @Test
    fun engine_beginRoute_usesSelectedLine_andFallsBackToFirst() {
        val selected = beginRoute(selected = mapOf(0 to "L1b"))
        assertEquals("L1b", selected.leg.lineId)
        assertEquals(4, selected.state.boardStopOrder)
        assertEquals(9, selected.state.targetOrder)

        val missing = beginRoute(selected = mapOf(0 to "nope"))
        assertEquals("L1", missing.leg.lineId)

        // 选中项只对自己那一程生效
        val otherLeg = beginRoute(selected = mapOf(1 to "L1b"))
        assertEquals("L1", otherLeg.leg.lineId)
    }

    @Test
    fun engine_beginRoute_startLegOutOfRange_isCoerced() {
        val tooBig = beginRoute(startLeg = 5)
        assertEquals(1, tooBig.state.currentLegIndex)
        assertEquals("L2", tooBig.leg.lineId)

        val negative = beginRoute(startLeg = -2)
        assertEquals(0, negative.state.currentLegIndex)
        assertEquals("L1", negative.leg.lineId)
    }

    @Test
    fun engine_beginRoute_withoutLegsOrLines_returnsNull() {
        assertNull(TrackingEngine.begin("s1", 1, routeTarget(legs = emptyList()), NOW_MILLIS))
        assertNull(TrackingEngine.begin("s1", 1, routeTarget(legs = listOf(leg0.copy(candidateLines = emptyList()))), NOW_MILLIS))
    }

    // ---------------- reducePoll ----------------

    @Test
    fun engine_reducePollSingle_arrivalEmitsArrivedOnce() {
        val session = beginSingle()
        val atStop = detail(listOf(singleBus("b1", order = 10, arrived = true, progress = 1f)))

        val first = poll(session, atStop)
        assertEquals(1, first.effects.size)
        val alert = assertIs<TrackingEngine.Effect.Alert>(first.effects.single())
        assertEquals(TrackingEngine.AlertKind.ARRIVED, alert.kind)
        assertTrue(alert.state.isBusArrivedAtStop)
        assertEquals(0, alert.state.stopsRemaining)
        assertTrue(first.session.state.hasTriggeredArrivedAlert)
        assertEquals(session.generation, first.session.generation)

        val second = poll(first.session, atStop)
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun engine_reducePollRoute_lockedBusAtTransferStop_switchesToNextLegWithTransferAlertOnly() {
        val session = beginRoute(generation = 3).onBoard("busX")
        val result = poll(session, detail(listOf(groupBus("busX", order = 8, arrived = true, progress = 1f))))

        assertEquals(listOf<TrackingEngine.Effect>(TrackingEngine.Effect.TransferAlert("换乘站B", "28路")), result.effects)
        val next = result.session
        assertEquals("s1", next.sessionId)
        assertEquals(4, next.generation)
        assertEquals(1, next.state.currentLegIndex)
        assertEquals(LegPhase.WAITING_BOARD, next.state.legPhase)
        assertNull(next.state.lockedBusId)
        assertEquals(2, next.state.boardStopOrder)
        assertEquals("换乘站B", next.state.boardStopName)
        assertEquals("终点站C", next.state.targetStopName)
        assertEquals(TrackingLegParams(cityId = "hz", lineId = "L2", lineName = "28路", targetStopId = "C", targetOrder = 6), next.leg)
    }

    @Test
    fun engine_reducePollRoute_transferUsesSelectedLineOfNextLeg() {
        val session = beginRoute(selected = mapOf(1 to "L2b")).onBoard("busX")
        val result = poll(session, detail(listOf(groupBus("busX", order = 8, arrived = true, progress = 1f))))
        assertEquals(listOf<TrackingEngine.Effect>(TrackingEngine.Effect.TransferAlert("换乘站B", "K28路")), result.effects)
        assertEquals(
            TrackingLegParams(
                cityId = "hz", lineId = "L2b", lineName = "K28路", targetStopId = "C", targetOrder = 10,
                source = DataSourceType.MYGOLBS, mygolbsCityCode = "0571", direction = 1
            ),
            result.session.leg
        )
    }

    @Test
    fun engine_reducePollRoute_lastLegArrival_emitsArrivedThenFinished() {
        val session = beginRoute(startLeg = 1, generation = 7).onBoard("busY")
        val result = poll(session, detail(listOf(groupBus("busY", order = 6, arrived = true, progress = 1f))))

        assertEquals(2, result.effects.size)
        val alert = assertIs<TrackingEngine.Effect.Alert>(result.effects[0])
        assertEquals(TrackingEngine.AlertKind.ARRIVED, alert.kind)
        assertSame(TrackingEngine.Effect.Finished, result.effects[1])
        // 结束前的最后状态：代际号不变，还是这一程
        assertEquals(7, result.session.generation)
        assertEquals(1, result.session.state.currentLegIndex)
        assertEquals("已顺利到达终点站 终点站C！", result.session.state.statusSummary)
    }

    @Test
    fun engine_reducePollRoute_onBoardNotArrived_noTransition() {
        val session = beginRoute().onBoard("busX")
        val result = poll(session, detail(listOf(groupBus("busX", order = 5))))
        assertTrue(result.effects.none { it is TrackingEngine.Effect.TransferAlert || it == TrackingEngine.Effect.Finished })
        assertEquals(session.generation, result.session.generation)
        assertEquals(0, result.session.state.currentLegIndex)
        assertEquals(LegPhase.ON_BOARD, result.session.state.legPhase)
    }

    @Test
    fun engine_reducePollRoute_waitingBusAtBoardStop_withoutLocation_boardsAndAlertsArrived() {
        val session = beginRoute()
        // 即便上车站有坐标、手上有站边的定位，locationEnabled=false 也不走定位判上车
        val result = poll(
            session,
            detail(listOf(boardingBus("busX", order = 3, metersNorth = 0, arrived = true)), listOf(boardStopWithCoords)),
            locationEnabled = false,
            serviceFix = serviceFixNorth(10)
        )
        assertEquals(LegPhase.ON_BOARD, result.session.state.legPhase)
        assertEquals("busX", result.session.state.lockedBusId)
        val alert = assertIs<TrackingEngine.Effect.Alert>(result.effects.single())
        assertEquals(TrackingEngine.AlertKind.ARRIVED, alert.kind)
        assertEquals("上车站A", alert.state.targetStopName)
        assertEquals(session.generation, result.session.generation)
    }

    @Test
    fun engine_reducePollRoute_waitingBusAtBoardStop_withFreshFixAtStop_entersCandidate() {
        val session = beginRoute()
        val result = poll(
            session,
            detail(listOf(boardingBus("busX", order = 3, metersNorth = 0, arrived = true)), listOf(boardStopWithCoords)),
            locationEnabled = true,
            serviceFix = serviceFixNorth(20)
        )
        assertEquals(LegPhase.WAITING_BOARD, result.session.state.legPhase)
        assertEquals("busX", result.session.state.boardingCandidateBusId)
        assertNull(result.session.state.lockedBusId)
        val alert = assertIs<TrackingEngine.Effect.Alert>(result.effects.single())
        assertEquals(TrackingEngine.AlertKind.ARRIVED, alert.kind)
    }

    @Test
    fun engine_reducePollRoute_boardStopWithoutCoords_fallsBackToBoardingWithoutLocation() {
        val session = beginRoute()
        val result = poll(
            session,
            detail(listOf(boardingBus("busX", order = 3, metersNorth = 0, arrived = true)), listOf(boardStopWithCoords.copy(wgsLat = 0.0, wgsLng = 0.0))),
            locationEnabled = true,
            serviceFix = serviceFixNorth(20)
        )
        assertEquals(LegPhase.ON_BOARD, result.session.state.legPhase)
        assertEquals("busX", result.session.state.lockedBusId)
    }

    @Test
    fun engine_reducePollRoute_staleOrPoorServiceFix_countsAsNoFixButStillUsesLocationFlow() {
        val session = beginRoute()
        val atStop = detail(listOf(boardingBus("busX", order = 3, metersNorth = 0, arrived = true)), listOf(boardStopWithCoords))

        // 对照：新鲜定位离站 2 km -> 不进入待确认，也不算上车
        val farFresh = poll(session, atStop, locationEnabled = true, serviceFix = serviceFixNorth(2000))
        assertNull(farFresh.session.state.boardingCandidateBusId)
        assertEquals(LegPhase.WAITING_BOARD, farFresh.session.state.legPhase)

        // 同一个点但超过 30 秒：等同没定位，仍有定位上下文 -> 先进入待确认（不是直接上车）
        val farStale = poll(session, atStop, locationEnabled = true, serviceFix = serviceFixNorth(2000, ageMillis = 30_001L))
        assertEquals("busX", farStale.session.state.boardingCandidateBusId)
        assertEquals(LegPhase.WAITING_BOARD, farStale.session.state.legPhase)

        // 精度差于 50 m 同理
        val farPoor = poll(session, atStop, locationEnabled = true, serviceFix = serviceFixNorth(2000, accuracy = 80f))
        assertEquals("busX", farPoor.session.state.boardingCandidateBusId)
        assertEquals(LegPhase.WAITING_BOARD, farPoor.session.state.legPhase)
    }

    @Test
    fun engine_reducePollRoute_onBoard_dropFixNearDropStop_switchesLeg() {
        // 下车站辅助判定：公交还差 2 站，但 dropFix 已在下车站边上
        val (userGcjLat, userGcjLng) = CoordTransform.wgs84ToGcj02(30.2740, 120.1550)
        val legWithCoords = leg0.copy(toStopLat = userGcjLat, toStopLng = userGcjLng)
        val target = routeTarget(legs = listOf(legWithCoords, leg1))
        val session = assertNotNull(TrackingEngine.begin("s1", 3, target, NOW_MILLIS)).onBoard("busX")
        val dropFix = UserFix(30.2740, 120.1550, 10f, ELAPSED_NOW - 1_000L)

        val result = poll(session, detail(listOf(groupBus("busX", order = 6))), dropFix = dropFix)
        assertTrue(result.effects.contains(TrackingEngine.Effect.TransferAlert("换乘站B", "28路")))
        assertEquals(4, result.session.generation)
        assertEquals(1, result.session.state.currentLegIndex)
    }

    @Test
    fun engine_reduceFailure_onlyChangesStatusSummary() {
        val session = beginRoute().onBoard("busX")
        val failed = TrackingEngine.reduceFailure(session, NOW_MILLIS + 5)
        assertEquals("实时数据刷新失败，正在重试…", failed.state.statusSummary)
        assertEquals(
            session.copy(state = session.state.copy(statusSummary = failed.state.statusSummary, updatedAtMillis = NOW_MILLIS + 5)),
            failed
        )
    }

    // ---------------- 用户操作 ----------------

    @Test
    fun engine_confirmBoarded_onlyInWaitingBoard_picksBusWithinAToAPlusOne() {
        assertNull(TrackingEngine.confirmBoarded(beginSingle(), emptyList(), NOW_MILLIS))
        assertNull(TrackingEngine.confirmBoarded(beginRoute().onBoard("busX"), emptyList(), NOW_MILLIS))

        val waiting = beginRoute()
        val buses = listOf(
            groupBus("next", order = 2, progress = 0.5f),
            groupBus("mine", order = 4, progress = 0.2f).copy(wgsLat = 30.0, wgsLng = 120.0),
            groupBus("farAhead", order = 6)
        )
        val confirmed = assertNotNull(TrackingEngine.confirmBoarded(waiting, buses, NOW_MILLIS + 9))
        assertEquals(LegPhase.ON_BOARD, confirmed.state.legPhase)
        assertEquals("mine", confirmed.state.lockedBusId)
        assertEquals(NOW_MILLIS + 9, confirmed.state.updatedAtMillis)
        assertEquals(waiting.generation, confirmed.generation)
        assertEquals(waiting.leg, confirmed.leg)
    }

    @Test
    fun engine_nextLeg_bumpsGeneration_orReturnsNull() {
        assertNull(TrackingEngine.nextLeg(beginSingle(), NOW_MILLIS))
        assertNull(TrackingEngine.nextLeg(beginRoute(startLeg = 1), NOW_MILLIS))

        val current = beginRoute(generation = 7).onBoard("busX")
        val next = assertNotNull(TrackingEngine.nextLeg(current, NOW_MILLIS + 1))
        assertEquals(8, next.generation)
        assertEquals("s1", next.sessionId)
        assertEquals(1, next.state.currentLegIndex)
        assertEquals(LegPhase.WAITING_BOARD, next.state.legPhase)
        assertNull(next.state.lockedBusId)
        assertEquals("L2", next.leg.lineId)

        val selected = assertNotNull(TrackingEngine.nextLeg(beginRoute(selected = mapOf(1 to "L2b")), NOW_MILLIS))
        assertEquals("L2b", selected.leg.lineId)
    }

    @Test
    fun engine_nextLegLineName() {
        assertEquals("28路", TrackingEngine.nextLegLineName(beginRoute()))
        assertEquals("K28路", TrackingEngine.nextLegLineName(beginRoute(selected = mapOf(1 to "L2b"))))
        assertNull(TrackingEngine.nextLegLineName(beginRoute(startLeg = 1)))
        assertNull(TrackingEngine.nextLegLineName(beginRoute(), legIndex = 1))
        assertNull(TrackingEngine.nextLegLineName(beginSingle()))
    }

    @Test
    fun engine_cycleThreshold_cyclesOneTwoThree() {
        val one = beginSingle(threshold = 1)
        val two = TrackingEngine.cycleThreshold(one, NOW_MILLIS + 1)
        assertEquals(2, two.state.alertThresholdStops)
        assertEquals(NOW_MILLIS + 1, two.state.updatedAtMillis)
        val three = TrackingEngine.cycleThreshold(two, NOW_MILLIS)
        assertEquals(3, three.state.alertThresholdStops)
        val back = TrackingEngine.cycleThreshold(three, NOW_MILLIS)
        assertEquals(1, back.state.alertThresholdStops)
    }

    @Test
    fun engine_cycleThreshold_resetsApproachingFlagOnlyWhenStillFartherThanNewThreshold() {
        val base = beginSingle(threshold = 2)
        val far = base.copy(state = base.state.copy(stopsRemaining = 5, hasTriggeredApproachingAlert = true))
        assertFalse(TrackingEngine.cycleThreshold(far, NOW_MILLIS).state.hasTriggeredApproachingAlert)

        val near = base.copy(state = base.state.copy(stopsRemaining = 2, hasTriggeredApproachingAlert = true))
        assertTrue(TrackingEngine.cycleThreshold(near, NOW_MILLIS).state.hasTriggeredApproachingAlert)

        // 3 -> 1：剩 2 站已比新阈值远，重新允许“即将到站”
        val nearAtThree = near.copy(state = near.state.copy(alertThresholdStops = 3))
        val toOne = TrackingEngine.cycleThreshold(nearAtThree, NOW_MILLIS)
        assertEquals(1, toOne.state.alertThresholdStops)
        assertFalse(toOne.state.hasTriggeredApproachingAlert)
    }

    // ---------------- 定位取舍与档位 ----------------

    private fun fixAt(elapsed: Long, accuracy: Float?) = UserFix(STOP_LAT, STOP_LNG, accuracy, elapsed)

    @Test
    fun engine_shouldAcceptServiceFix_keepsGoodFixAgainstPoorOneFor15s() {
        val good = fixAt(ELAPSED_NOW, 10f)
        assertTrue(TrackingEngine.shouldAcceptServiceFix(null, fixAt(ELAPSED_NOW, 200f)))
        // 差定位在 15 秒内不顶掉好定位
        assertFalse(TrackingEngine.shouldAcceptServiceFix(good, fixAt(ELAPSED_NOW + 15_000L, 80f)))
        assertFalse(TrackingEngine.shouldAcceptServiceFix(good, fixAt(ELAPSED_NOW + 5_000L, null)))
        // 超过 15 秒就顶掉
        assertTrue(TrackingEngine.shouldAcceptServiceFix(good, fixAt(ELAPSED_NOW + 15_001L, 80f)))
        // 好定位总是接受
        assertTrue(TrackingEngine.shouldAcceptServiceFix(good, fixAt(ELAPSED_NOW + 1_000L, 50f)))
        assertTrue(TrackingEngine.shouldAcceptServiceFix(fixAt(ELAPSED_NOW, 200f), fixAt(ELAPSED_NOW + 1_000L, 20f)))
        // 手上的也是差定位：换成新的
        assertTrue(TrackingEngine.shouldAcceptServiceFix(fixAt(ELAPSED_NOW, 200f), fixAt(ELAPSED_NOW + 1_000L, 80f)))
    }

    @Test
    fun engine_usableBoardingFix_30sAnd50mBoundaries() {
        assertNull(TrackingEngine.usableBoardingFix(null, ELAPSED_NOW))
        val edge = fixAt(ELAPSED_NOW - 30_000L, 50f)
        assertSame(edge, TrackingEngine.usableBoardingFix(edge, ELAPSED_NOW))
        assertNull(TrackingEngine.usableBoardingFix(fixAt(ELAPSED_NOW - 30_001L, 10f), ELAPSED_NOW))
        assertNull(TrackingEngine.usableBoardingFix(fixAt(ELAPSED_NOW, 50.5f), ELAPSED_NOW))
        assertNull(TrackingEngine.usableBoardingFix(fixAt(ELAPSED_NOW, null), ELAPSED_NOW))
        // 定位时刻在“现在”之后：不用
        assertNull(TrackingEngine.usableBoardingFix(fixAt(ELAPSED_NOW + 1L, 10f), ELAPSED_NOW))
    }

    @Test
    fun engine_locationTier_isOffWhenLocationDisabled() {
        val waitingNear = beginRoute().state.copy(stopsRemaining = 1)
        assertEquals(LocationCadence.Tier.WAITING_NEAR, TrackingEngine.locationTier(waitingNear, locationEnabled = true))
        assertEquals(LocationCadence.Tier.OFF, TrackingEngine.locationTier(waitingNear, locationEnabled = false))

        val riding = beginRoute().onBoard("busX").state.copy(stopsRemaining = 5)
        assertEquals(LocationCadence.Tier.RIDING, TrackingEngine.locationTier(riding, locationEnabled = true))
        assertEquals(LocationCadence.Tier.OFF, TrackingEngine.locationTier(riding, locationEnabled = false))
    }

    @Test
    fun engine_wantsLocation_onlyForRoutes() {
        assertTrue(TrackingEngine.wantsLocation(routeTarget()))
        assertFalse(TrackingEngine.wantsLocation(singleTarget()))
    }
}
