package com.trackr.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 要追踪什么（重构计划 §2.6）。开始追踪时由 UI / 提醒传给 `TrackingController.start`，存在会话里，进程重建后据此继续。
 * 第 15 步加 `WatchTogether`（一起等多条线）。
 */
@Serializable
sealed interface TrackingTarget {

    /** 单线路：等某条线的车到某一站 */
    @Serializable
    @SerialName("single")
    data class SingleLine(
        val cityId: String,
        val lineId: String,
        val lineName: String,
        val lineNo: String = "",
        val endStopName: String = "",
        val targetStopId: String = "",
        val targetStopName: String = "",
        val targetOrder: Int = 1,
        val totalStops: Int = targetOrder,
        val alertThresholdStops: Int = 2,
        val source: DataSourceType = DataSourceType.CHELAILE,
        val mygolbsCityCode: String = "",
        val direction: Int = 0
    ) : TrackingTarget

    /**
     * 路线（分组换乘）：从第 [startLeg] 程开始，每程用 [selectedLineIds]（legIndex -> lineId）里选中的候选线，
     * 找不到时用该程第一条候选线。[group] 是开始时的整条路线（临时方案不在收藏里，只能存在这里）。
     */
    @Serializable
    @SerialName("route")
    data class Route(
        val group: TransitGroup,
        val startLeg: Int = 0,
        val selectedLineIds: Map<Int, String> = emptyMap()
    ) : TrackingTarget
}

/**
 * 当前这一程的轮询参数（原 `TrackingStartParams` 里和请求有关的部分）。
 * 单线路就是目标本身；路线是当前程选中的候选线。
 */
@Serializable
data class TrackingLegParams(
    val cityId: String,
    val lineId: String,
    val lineName: String,
    val targetStopId: String = "",
    val targetOrder: Int = 1,
    val source: DataSourceType = DataSourceType.CHELAILE,
    val mygolbsCityCode: String = "",
    val direction: Int = 0
)

/**
 * 一次追踪会话（`TrackingRepository` 里唯一的一份，存 `tracking_session.json`）。
 *
 * @param sessionId 这次会话的标识，服务只收它
 * @param generation 代际号：开始 / 切程 / 停止都 +1；轮询结果回来先比对，旧的直接丢
 * @param leg 当前这一程的轮询参数
 * @param state 当前这一程的状态（通知、首页追踪卡、收藏页都读它）
 * @param interrupted 进程被杀后读回的会话，追踪服务还没接上（接上后清掉）
 */
@Serializable
data class TrackingSession(
    val sessionId: String,
    val generation: Int,
    val target: TrackingTarget,
    val leg: TrackingLegParams,
    val state: TrackingSessionState,
    val interrupted: Boolean = false
) {
    /** 路线追踪时的整条路线；单线路为 null */
    val group: TransitGroup?
        get() = (target as? TrackingTarget.Route)?.group

    /** 收藏页 / 规划页用的换乘组视图；单线路为 null */
    fun toGroupSessionState(): GroupTrackingSessionState? {
        val group = group ?: return null
        val legIndex = state.currentLegIndex
        return GroupTrackingSessionState(
            groupId = group.id,
            groupName = group.name,
            activeLegIndex = legIndex,
            totalLegs = group.legs.size,
            currentLeg = group.legs.getOrNull(legIndex),
            trackingSessionState = state,
            isCompleted = false
        )
    }
}
