package com.trackr.ui.screens.more

/** 「高德 Key」区块里那一行的状态文字（第 17 步从 AmapKeySection.kt 挪到 commonMain，commonTest 要测它） */
internal fun amapKeyStatusText(hasMapKey: Boolean, canShowMap: Boolean): String = when {
    canShowMap -> "已配置，路线规划显示地图"
    hasMapKey -> "只有 Web 服务 Key：能算路线，不显示地图"
    else -> "未配置，不显示「路线规划」"
}
