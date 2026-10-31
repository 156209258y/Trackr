package com.trackr.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 还没写进 commonMain 的页面在非 Android 平台上的空壳（第 17 步：旧页面都在 androidMain，
 * 阶段 D 逐页重写进 commonMain 后删掉）。也给计划里先占位的新页面（路线详情、编辑收藏）用。
 */
@Composable
fun PendingScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$title（开发中）")
    }
}
