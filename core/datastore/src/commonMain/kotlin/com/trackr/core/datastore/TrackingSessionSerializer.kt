package com.trackr.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import com.trackr.core.model.TrackingSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

/**
 * 追踪会话存成一个 JSON 对象（文件 `tracking_session.json`）。每次轮询整条覆盖写，从不查询（§2.4）。
 * 空文件 = 没有会话；解析失败抛 [CorruptionException]，由 DataStore 的损坏处理换成 null。
 * `TrackingRepository`（第 12 步）经 `DataStoreTrackingSessionStore` 读写。
 */
object TrackingSessionSerializer : OkioSerializer<TrackingSession?> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: TrackingSession? = null

    override suspend fun readFrom(source: BufferedSource): TrackingSession? {
        val text = source.readUtf8()
        if (text.isBlank()) return null
        return try {
            json.decodeFromString(TrackingSession.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("tracking_session.json 解析失败", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("tracking_session.json 解析失败", e)
        }
    }

    override suspend fun writeTo(t: TrackingSession?, sink: BufferedSink) {
        if (t != null) {
            sink.writeUtf8(json.encodeToString(TrackingSession.serializer(), t))
        }
    }
}
