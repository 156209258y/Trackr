package com.trackr

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trackr.app.TrackrRoot
import com.trackr.core.model.TransitCodeProvider
import com.trackr.ui.navigation.PendingDeepLink
import com.trackr.util.TransitCodeHelper

/**
 * 应用唯一的 Activity：edge-to-edge、读 Intent（小组件 / 快捷方式 / 通知的跳转，乘车码快捷方式），
 * 其余都在 `:composeApp` 的 [TrackrRoot] 里（第 17 步，重构计划 §4.2）。
 */
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<PendingDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        handleTransitCodeIntent(intent)

        // 仅在首次启动时消费 Deep Link（避免旋转屏幕等配置变更时重复追加到栈顶）
        if (savedInstanceState == null) {
            extractAndConsumeDeepLink(intent)
        }

        // 根 Composable（主题、底栏、导航）第 17 步搬进 :composeApp 的 TrackrRoot
        setContent {
            TrackrRoot(
                pendingDeepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTransitCodeIntent(intent)
        extractAndConsumeDeepLink(intent)
    }

    private fun extractAndConsumeDeepLink(intent: Intent?) {
        if (intent == null) return
        val cityId = intent.getStringExtra("extra_city_id")
        val lineId = intent.getStringExtra("extra_line_id")
        if (!cityId.isNullOrBlank() && !lineId.isNullOrBlank()) {
            pendingDeepLink = PendingDeepLink(
                cityId = cityId,
                lineId = lineId,
                targetOrder = intent.getIntExtra("extra_target_order", 0),
                stopId = intent.getStringExtra("extra_stop_id").orEmpty(),
                sourceName = intent.getStringExtra("extra_source").orEmpty(),
                lineName = intent.getStringExtra("extra_line_name").orEmpty()
            )
            // 一次性消费：移除 Intent 中的 Deep Link 参数，杜绝后续重放
            intent.removeExtra("extra_city_id")
            intent.removeExtra("extra_line_id")
            intent.removeExtra("extra_target_order")
            intent.removeExtra("extra_stop_id")
            intent.removeExtra("extra_source")
            intent.removeExtra("extra_line_name")
        }
    }

    private fun handleTransitCodeIntent(intent: Intent?) {
        if (intent?.action == "com.trackr.action.OPEN_TRANSIT_CODE" ||
            intent?.action == "com.github.u5151561.trackr.action.OPEN_TRANSIT_CODE"
        ) {
            val providerStr = intent.getStringExtra("extra_provider")
            val provider = if (providerStr == "alipay") TransitCodeProvider.ALIPAY else TransitCodeProvider.UNIONPAY
            TransitCodeHelper.openTransitCode(this, provider)
            intent.removeExtra("extra_provider")
            intent.action = null
        }
    }
}
