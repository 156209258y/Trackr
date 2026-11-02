package com.trackr.data.remote.update

import android.os.Build
import com.trackr.core.domain.platform.UpdateChecker
import com.trackr.core.model.AppUpdateInfo
import com.trackr.core.model.UpdateCheckResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * 基于 GitHub / CDN 的应用更新检测器（只在 Android，重构计划 §2.9）
 */
class GithubUpdateChecker(
    private val httpClient: HttpClient,
    private val endpointUrls: List<String> = DEFAULT_ENDPOINTS,
    /** 当前设备支持的 ABI，按优先级排列；用在高德原生库按 ABI 分包后挑对应安装包 */
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
) : UpdateChecker {

    companion object {
        val DEFAULT_ENDPOINTS = listOf(
            "https://raw.githubusercontent.com/5151561/Trackr/update/update.json",
            "https://fastly.jsdelivr.net/gh/5151561/Trackr@update/update.json",
            "https://cdn.jsdelivr.net/gh/5151561/Trackr@update/update.json"
        )

        /** 更新包下载地址允许的域名白名单（精确匹配，不含子域名） */
        val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "raw.githubusercontent.com",
            "cdn.jsdelivr.net",
            "fastly.jsdelivr.net"
        )

        /**
         * 校验下载地址：必须是 https，且 host 在 [ALLOWED_DOWNLOAD_HOSTS] 白名单内
         */
        fun isAllowedDownloadUrl(url: String): Boolean {
            val trimmed = url.trim()
            // Ktor 解析不带协议的串时会默认补 http；这里要求原串明确以 https:// 开头
            if (!trimmed.startsWith("https://", ignoreCase = true)) return false
            val parsed = parseUrl(trimmed) ?: return false
            return parsed.protocol == URLProtocol.HTTPS && parsed.host.lowercase() in ALLOWED_DOWNLOAD_HOSTS
        }
    }

    override suspend fun checkForUpdates(
        currentVersionCode: Int,
        currentVersionName: String
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        var lastException: Throwable? = null

        for (url in endpointUrls) {
            try {
                val response = httpClient.get(url) {
                    header("User-Agent", "Trackr-Android-App/$currentVersionName")
                    header("Cache-Control", "no-cache")
                }
                if (!response.status.isSuccess()) {
                    lastException = IOException("HTTP ${response.status.value}: ${response.status.description}")
                    continue
                }

                val bodyString = response.bodyAsText()
                if (bodyString.isBlank()) {
                    lastException = IOException("Empty response body from $url")
                    continue
                }

                val updateInfo = json.decodeFromString<AppUpdateInfo>(bodyString)
                // 按 ABI 选包；细分的地址不合规（非 https / 域名不在白名单）时回退到通用地址
                val pickedUrl = updateInfo.pickDownloadUrl(supportedAbis)
                val downloadUrl = when {
                    isAllowedDownloadUrl(pickedUrl) -> pickedUrl
                    isAllowedDownloadUrl(updateInfo.downloadUrl) -> updateInfo.downloadUrl
                    else -> null
                }
                if (downloadUrl == null) {
                    // 下载地址不合规（非 https 或域名不在白名单）视为该源出错，继续尝试下一个源
                    lastException = IOException("更新下载地址不合规：$pickedUrl")
                    continue
                }
                val resolvedInfo = updateInfo.copy(downloadUrl = downloadUrl)
                val hasUpdate = resolvedInfo.versionCode > currentVersionCode
                val isForce = resolvedInfo.forceUpdate || currentVersionCode < resolvedInfo.minSupportedVersionCode

                return@withContext if (hasUpdate) {
                    UpdateCheckResult.HasUpdate(
                        updateInfo = resolvedInfo,
                        isForceUpdate = isForce
                    )
                } else {
                    UpdateCheckResult.UpToDate(currentVersionName)
                }
            } catch (ce: CancellationException) {
                // 页面离开时取消检查，不再挨个试剩下的源
                throw ce
            } catch (e: Exception) {
                lastException = e
            }
        }

        UpdateCheckResult.Error(
            message = lastException?.localizedMessage ?: "未能连接到更新服务器，请检查网络后重试",
            cause = lastException
        )
    }
}
