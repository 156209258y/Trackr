package com.trackr.core.network.remote.common

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

internal actual fun platformHttpClient(configure: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(NetworkTimeouts.CONNECT_MS, TimeUnit.MILLISECONDS)
                readTimeout(NetworkTimeouts.READ_MS, TimeUnit.MILLISECONDS)
                writeTimeout(NetworkTimeouts.WRITE_MS, TimeUnit.MILLISECONDS)
                // 整个调用（含重定向与重试）的总时限，和 HttpTimeout 插件的总超时一致
                callTimeout(NetworkTimeouts.REQUEST_MS, TimeUnit.MILLISECONDS)
            }
        }
        configure()
    }
