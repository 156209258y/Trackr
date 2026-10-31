package com.trackr.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.trackr.core.domain.platform.LocationSource
import com.trackr.core.domain.platform.MonotonicClock
import com.trackr.core.domain.tracking.LocationCadence
import com.trackr.core.model.UserFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/** Android 的单调时钟：`SystemClock.elapsedRealtime()`，和 `Location.elapsedRealtimeNanos` 同一时基 */
val AndroidMonotonicClock: MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }

/**
 * [LocationSource] 的 Android 实现（系统 LocationManager，WGS-84）。
 *
 * 原来的 `AndroidDeviceLocationProvider` 拆成两半：取 lastKnown、开监听这些系统调用留在这里，
 * 「够新够准就直接用、否则窗口内取最准」的算法搬进 `:core:data` 的 `DefaultLocationRepository`（第 8 步）。
 * 追踪服务的 `ServiceLocationSource` 第 12 步并进来（届时 [fixes] 换成它的多源合并写法）。
 */
class AndroidLocationSource(context: Context) : LocationSource {

    private val appContext: Context = context.applicationContext ?: context
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun hasPrecisePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasAnyPermission(): Boolean =
        hasPrecisePermission() ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 各个已开启的 provider 缓存的最近定位（fused / network / gps / passive，各一个） */
    override fun lastKnownFixes(): List<UserFix> {
        if (!hasAnyPermission()) return emptyList()
        val lm = locationManager ?: return emptyList()
        return LAST_KNOWN_PROVIDERS.mapNotNull { provider ->
            try {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider)?.toUserFix() else null
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    override fun lastKnown(): UserFix? = lastKnownFixes().maxByOrNull { it.elapsedMillis }

    /** 在 fused / network / gps 上以最高频率（0 毫秒、0 米）监听，收集结束即注销 */
    override fun currentFixes(): Flow<UserFix> = listen(ACTIVE_PROVIDERS, minTimeMillis = 0L)

    /**
     * 按档位持续定位：高精度档位同时听 gps 和 network，省电档只听 network。
     * 第 12 步换成 `ServiceLocationSource` 的写法（多源同时回调时差的不顶掉好的）。
     */
    override fun fixes(tier: LocationCadence.Tier): Flow<UserFix> {
        if (tier == LocationCadence.Tier.OFF) return emptyFlow()
        val providers = if (tier.highAccuracy) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        return listen(providers, minTimeMillis = tier.intervalMillis)
    }

    private fun listen(providers: List<String>, minTimeMillis: Long): Flow<UserFix> = callbackFlow {
        val lm = locationManager
        if (lm == null || !hasAnyPermission()) {
            close()
            return@callbackFlow
        }
        val enabled = providers.filter { provider ->
            try {
                lm.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toUserFix())
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        var requestedAny = false
        for (provider in enabled) {
            try {
                lm.requestLocationUpdates(provider, minTimeMillis, 0f, listener, Looper.getMainLooper())
                requestedAny = true
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        if (!requestedAny) {
            close()
        }
        awaitClose {
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        val LAST_KNOWN_PROVIDERS = listOf(
            "fused",
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val ACTIVE_PROVIDERS = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}

/**
 * 系统定位转成 [UserFix]。定位时刻优先用单调时钟 `elapsedRealtimeNanos`（不受用户改系统时间影响），
 * 缺失（为 0）时按墙钟年龄折算到同一时基。
 */
internal fun Location.toUserFix(): UserFix {
    val elapsedMillis = if (elapsedRealtimeNanos > 0L) {
        elapsedRealtimeNanos / 1_000_000L
    } else {
        SystemClock.elapsedRealtime() - (System.currentTimeMillis() - time)
    }
    return UserFix(
        lat = latitude,
        lng = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        elapsedMillis = elapsedMillis
    )
}
