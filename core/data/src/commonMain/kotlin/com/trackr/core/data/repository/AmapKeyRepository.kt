package com.trackr.core.data.repository

import com.trackr.core.data.local.AmapKeyStore
import com.trackr.core.data.local.SerialWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户自己填的高德 Key，两槽严格分开、互不回落（0a4fdab / 8a3e570）：
 * - [androidKey]：地图 / 搜索 / 定位 SDK 用，和包名 + 签名 SHA-1 绑定；
 * - [webKey]：restapi.amap.com 用，不绑定包名。
 *
 * 计划 §2.5 把它归在 SettingsRepository 下；第 9 步先单独成一个接口（存在单独的文件里、备份排除），
 * 第 11 步加 `hasMapKey` / `canShowMap` 时再看要不要并进去。
 */
interface AmapKeyRepository {
    val androidKey: StateFlow<String?>
    val webKey: StateFlow<String?>

    /** 立刻生效，后台按顺序写盘；空串等于清空 */
    fun setAndroidKey(key: String?)
    fun setWebKey(key: String?)
}

/** 读写都经 [AmapKeyStore]；用 [create] 建，建好时两个值已经读出来 */
class DefaultAmapKeyRepository private constructor(
    private val store: AmapKeyStore,
    private val writer: SerialWriter,
    initialAndroidKey: String?,
    initialWebKey: String?
) : AmapKeyRepository {

    private val _androidKey = MutableStateFlow(initialAndroidKey)
    override val androidKey: StateFlow<String?> = _androidKey.asStateFlow()

    private val _webKey = MutableStateFlow(initialWebKey)
    override val webKey: StateFlow<String?> = _webKey.asStateFlow()

    override fun setAndroidKey(key: String?) {
        val value = key?.trim()?.ifBlank { null }
        _androidKey.value = value
        writer.enqueue { store.saveAndroidKey(value) }
    }

    override fun setWebKey(key: String?) {
        val value = key?.trim()?.ifBlank { null }
        _webKey.value = value
        writer.enqueue { store.saveWebKey(value) }
    }

    /** 等排队的写入做完（测试用） */
    suspend fun flush() = writer.flush()

    companion object {
        suspend fun create(store: AmapKeyStore, writer: SerialWriter): DefaultAmapKeyRepository =
            DefaultAmapKeyRepository(store, writer, store.loadAndroidKey(), store.loadWebKey())
    }
}
