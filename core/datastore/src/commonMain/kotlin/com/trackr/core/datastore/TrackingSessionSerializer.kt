package com.trackr.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import com.trackr.core.model.TrackingSessionState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * 追踪会话存成一个 JSON 对象（文件 `tracking_session.json`）。每次轮询整条覆盖写，从不查询（§2.4）。
 * 空文件 = 没有会话；解析失败抛 [CorruptionException]，由 DataStore 的损坏处理换成 null。
 * 第 9 步只建好存储，第 12 步 `TrackingRepository` 接入。
 */
object TrackingSessionSerializer : OkioSerializer<TrackingSessionState?> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: TrackingSessionState? = null

    override suspend fun readFrom(source: BufferedSource): TrackingSessionState? {
        val text = source.readUtf8()
        if (text.isBlank()) return null
        return try {
            json.decodeFromString(TrackingSessionState.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("tracking_session.json 解析失败", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("tracking_session.json 解析失败", e)
        }
    }

    override suspend fun writeTo(t: TrackingSessionState?, sink: BufferedSink) {
        if (t != null) {
            sink.writeUtf8(json.encodeToString(TrackingSessionState.serializer(), t))
        }
    }
}
