package com.trackr.core.data.repository

import androidx.datastore.core.DataStore
import com.trackr.core.data.local.SerialWriter
import com.trackr.core.domain.platform.MonotonicClock
import com.trackr.core.domain.tracking.TrackingEngine
import com.trackr.core.model.GroupTrackingSessionState
import com.trackr.core.model.LiveBus
import com.trackr.core.model.TrackingSession
import com.trackr.core.model.TrackingSessionState
import com.trackr.core.model.TrackingTarget
import com.trackr.core.model.UserFix
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 追踪会话的唯一数据源（重构计划 §2.6）。
 *
 * UI、通知动作、追踪服务都读写这里：UI 和通知动作发命令（开始 / 我上车了 / 下一程 / 改提醒站数 / 结束），
 * 追踪服务轮询后用 [update] 写回（先核对代际号，旧的结果直接丢）。会话落盘，进程被杀后读回来继续。
 */
interface TrackingRepository {

    /** 当前会话；没在追踪时为 null */
    val session: StateFlow<TrackingSession?>

    /** 当前这一程的状态（首页追踪卡、线路页读它）；没在追踪、或会话中断待恢复时 `isActive = false` */
    val legState: StateFlow<TrackingSessionState>

    /** 路线追踪的换乘组视图（收藏页、规划页读它）；单线路或没在追踪时为 null */
    val groupSession: StateFlow<GroupTrackingSessionState?>

    /** 下车站辅助判定用的最近一次手机定位（服务定位和 App 前台定位里最后来的那个）；代际号变了就清空 */
    val latestUserFix: UserFix?

    /** 开始一次新会话（替换掉正在进行的），返回新会话；目标无效（路线没有可坐的线）时返回 null */
    fun start(target: TrackingTarget): TrackingSession?

    /** 结束追踪：清掉会话 */
    fun stop()

    /**
     * 开始失败（后台不允许启动前台服务）时放回开始前的会话 [previous]（代际号 +1，服务会重开它的轮询）；
     * 为 null 时等同 [stop]。只在会话仍是 [failed] 时生效。
     */
    fun restore(failed: TrackingSession, previous: TrackingSession?)

    /**
     * 手动「我已上车」；不在等车阶段时什么都不做。
     * 一起等：不同线的车同时在站、定位分不清时写 `boardChoice`（等 [chooseBoardedBus]），否则上那辆、会话切到那条线。
     */
    fun confirmBoarded()

    /** 一起等「上了哪辆？」：选了 [busId]，或 null =「还没上」（清掉待选接着等）；没有待选时什么都不做 */
    fun chooseBoardedBus(busId: String?)

    /** 手动切到下一程；不是路线或已是最后一程时什么都不做 */
    fun switchToNextLeg()

    /** 切换提前几站提醒（1 -> 2 -> 3 -> 1） */
    fun cycleThreshold()

    /** 记下一次手机定位（下车站辅助判定用） */
    fun updateUserFix(fix: UserFix)

    /**
     * 追踪服务写回轮询结果：只在会话仍是第 [generation] 代时才应用 [transform]（在当前值上算，
     * 期间用户点过「我上车了」等也不会被覆盖）。[transform] 返回 null 表示结束会话。
     * @return 是否应用了（false = 结果已过期，丢弃）
     */
    fun update(generation: Int, transform: (TrackingSession) -> TrackingSession?): Boolean

    /** 追踪服务记下最近一次轮询拿到的车（「我上车了」选车用，不落盘）；代际号不对时忽略 */
    fun recordBuses(generation: Int, buses: List<LiveBus>)

    /**
     * 一起等：追踪服务记下最近一次轮询每条线的车（lineId -> 车，「我上车了」选车用，不落盘）。
     * 只更新这次拿到的线，没请求的线保留上次的；代际号不对时忽略。
     */
    fun recordLineBuses(generation: Int, lineBuses: Map<String, List<LiveBus>>)

    /**
     * 追踪服务接上了一个从盘里读回的（中断的）会话：清掉中断标记，代际号 +1。
     * @return 接上后的会话；没有会话时为 null
     */
    fun resume(): TrackingSession?

    /** 等盘里的会话读完（服务被系统重建时先等它） */
    suspend fun awaitLoaded()
}

/** 会话怎么落盘（新存储：DataStore `tracking_session.json`；旧存储：只在内存里） */
interface TrackingSessionStore {
    suspend fun read(): TrackingSession?
    suspend fun write(session: TrackingSession?)
}

/** 不落盘的会话存储（旧存储 USE_NEW_STORE=old 时用，和第 12 步之前一样：进程没了会话就没了） */
class InMemoryTrackingSessionStore : TrackingSessionStore {
    private var value: TrackingSession? = null
    override suspend fun read(): TrackingSession? = value
    override suspend fun write(session: TrackingSession?) {
        value = session
    }
}

/** DataStore 版会话存储 */
class DataStoreTrackingSessionStore(private val dataStore: DataStore<TrackingSession?>) : TrackingSessionStore {
    override suspend fun read(): TrackingSession? = dataStore.data.first()
    override suspend fun write(session: TrackingSession?) {
        dataStore.updateData { session }
    }
}

/**
 * [TrackingRepository] 的实现：内存里一份（加锁），每次变化排队写盘。
 *
 * @param scope 读盘、写盘用的作用域（应用级）
 * @param clock 墙钟（写进 `updatedAtMillis`、判断读回的会话是否太旧）
 * @param monotonicClock 和定位同一时基的单调时钟（一起等「我上车了」用定位分辨上了哪辆）；null = 不用定位分辨
 * @param newSessionId 新会话的标识
 */
class DefaultTrackingRepository @OptIn(ExperimentalUuidApi::class) constructor(
    private val store: TrackingSessionStore,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val monotonicClock: MonotonicClock? = null,
    private val newSessionId: () -> String = { Uuid.random().toString() }
) : TrackingRepository {

    private val lock = SynchronizedObject()
    private val writer = SerialWriter(scope)

    private val _session = MutableStateFlow<TrackingSession?>(null)
    override val session: StateFlow<TrackingSession?> = _session.asStateFlow()

    private val _legState = MutableStateFlow(TrackingSessionState())
    override val legState: StateFlow<TrackingSessionState> = _legState.asStateFlow()

    private val _groupSession = MutableStateFlow<GroupTrackingSessionState?>(null)
    override val groupSession: StateFlow<GroupTrackingSessionState?> = _groupSession.asStateFlow()

    /** 已经用过的最大代际号（停止后也不回退，旧结果永远比不上） */
    private var lastGeneration = 0

    /** 读盘完成前就有人开始 / 结束过：读回来的会话作废 */
    private var touched = false

    private var lastBuses: List<LiveBus> = emptyList()

    /** 一起等：最近一次轮询每条线的车 */
    private var lastLineBuses: Map<String, List<LiveBus>> = emptyMap()

    private var userFix: UserFix? = null
    override val latestUserFix: UserFix?
        get() = synchronized(lock) { userFix }

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val stored = try {
                store.read()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            synchronized(lock) {
                if (stored != null) lastGeneration = maxOf(lastGeneration, stored.generation)
                if (stored != null && !touched && _session.value == null) {
                    val age = nowMillis() - stored.state.updatedAtMillis
                    // 年龄为负 = 用户把系统时间往回调了，照样接着追
                    if (age <= MAX_RESUME_AGE_MILLIS) {
                        // 进程被杀后读回：先标成中断，服务接上后再清掉
                        publish(stored.copy(interrupted = true), persist = false)
                    } else {
                        writer.enqueue { store.write(null) }
                    }
                }
            }
            loaded.complete(Unit)
        }
    }

    override suspend fun awaitLoaded() {
        loaded.await()
    }

    override fun start(target: TrackingTarget): TrackingSession? = synchronized(lock) {
        // 目标无效时什么都不动（不占代际号，也不作废盘里读回的会话）
        val generation = maxOf(lastGeneration, _session.value?.generation ?: 0) + 1
        val created = TrackingEngine.begin(newSessionId(), generation, target, nowMillis()) ?: return@synchronized null
        lastGeneration = generation
        touched = true
        lastBuses = emptyList()
        lastLineBuses = emptyMap()
        userFix = null
        publish(created)
        created
    }

    override fun stop() {
        synchronized(lock) {
            touched = true
            if (_session.value != null) nextGeneration()
            lastBuses = emptyList()
            lastLineBuses = emptyMap()
            userFix = null
            publish(null)
        }
    }

    override fun restore(failed: TrackingSession, previous: TrackingSession?) {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.sessionId != failed.sessionId || current.generation != failed.generation) return
            lastBuses = emptyList()
            lastLineBuses = emptyMap()
            if (previous == null) {
                nextGeneration()
                publish(null)
            } else {
                publish(previous.copy(generation = nextGeneration()))
            }
        }
    }

    override fun confirmBoarded() {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.interrupted) return
            val fixForBoarding = monotonicClock?.let { userFix }
            val boarded = TrackingEngine.confirmBoarded(
                session = current,
                lastBuses = lastBuses,
                nowMillis = nowMillis(),
                lineBuses = lastLineBuses,
                fix = fixForBoarding,
                nowElapsedMillis = monotonicClock?.elapsedMillis() ?: 0L
            ) ?: return
            publishMaybeNewGeneration(current, boarded)
        }
    }

    override fun chooseBoardedBus(busId: String?) {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.interrupted) return
            val chosen = TrackingEngine.chooseBoardedBus(current, busId, nowMillis()) ?: return
            publishMaybeNewGeneration(current, chosen)
        }
    }

    /** 调用方已持锁：一起等上车会切到那条线（代际号 +1），上一阶段的车不再用 */
    private fun publishMaybeNewGeneration(current: TrackingSession, next: TrackingSession) {
        if (next.generation != current.generation) {
            lastGeneration = maxOf(lastGeneration, next.generation)
            lastBuses = emptyList()
            lastLineBuses = emptyMap()
        }
        publish(next)
    }

    override fun switchToNextLeg() {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.interrupted) return
            val next = TrackingEngine.nextLeg(current, nowMillis()) ?: return
            lastGeneration = maxOf(lastGeneration, next.generation)
            lastBuses = emptyList()
            lastLineBuses = emptyMap()
            userFix = null
            publish(next)
        }
    }

    override fun cycleThreshold() {
        synchronized(lock) {
            val current = _session.value ?: return
            if (current.interrupted || !current.state.isActive) return
            publish(TrackingEngine.cycleThreshold(current, nowMillis()))
        }
    }

    override fun updateUserFix(fix: UserFix) {
        synchronized(lock) { userFix = fix }
    }

    override fun update(generation: Int, transform: (TrackingSession) -> TrackingSession?): Boolean =
        synchronized(lock) {
            val current = _session.value
            if (current == null || current.interrupted || current.generation != generation) return@synchronized false
            val next = transform(current)
            if (next == null) {
                nextGeneration()
                lastBuses = emptyList()
                lastLineBuses = emptyMap()
                userFix = null
            } else if (next.generation != current.generation) {
                // 引擎切了程：上一程的车和定位都不再用
                lastGeneration = maxOf(lastGeneration, next.generation)
                lastBuses = emptyList()
                lastLineBuses = emptyMap()
                userFix = null
            }
            publish(next)
            true
        }

    override fun recordBuses(generation: Int, buses: List<LiveBus>) {
        synchronized(lock) {
            if (_session.value?.generation == generation) lastBuses = buses
        }
    }

    override fun recordLineBuses(generation: Int, lineBuses: Map<String, List<LiveBus>>) {
        synchronized(lock) {
            if (_session.value?.generation == generation) lastLineBuses = lastLineBuses + lineBuses
        }
    }

    override fun resume(): TrackingSession? = synchronized(lock) {
        val current = _session.value ?: return@synchronized null
        if (!current.interrupted) return@synchronized current
        val resumed = current.copy(interrupted = false, generation = nextGeneration())
        lastBuses = emptyList()
        lastLineBuses = emptyMap()
        publish(resumed)
        resumed
    }

    /** 调用方已持锁 */
    private fun nextGeneration(): Int {
        val current = _session.value?.generation ?: 0
        lastGeneration = maxOf(lastGeneration, current) + 1
        return lastGeneration
    }

    /** 调用方已持锁：更新三个 StateFlow，并排队写盘 */
    private fun publish(value: TrackingSession?, persist: Boolean = true) {
        _session.value = value
        val live = value?.takeIf { !it.interrupted }
        _legState.value = live?.state ?: TrackingSessionState(isActive = false)
        _groupSession.value = live?.toGroupSessionState()
        if (persist) writer.enqueue { store.write(value) }
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    /** 等写盘排队的都做完（测试用） */
    suspend fun flush() = writer.flush()

    companion object {
        /** 读回的会话超过这么久没更新就不再接着追（多半是关机了一整夜） */
        const val MAX_RESUME_AGE_MILLIS: Long = 2 * 60 * 60 * 1000L
    }
}
