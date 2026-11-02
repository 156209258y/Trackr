package com.trackr.ui.bottombar

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 底栏（页签）收起时，页面底部贴着它的悬浮元素要跟着往下挪的距离（px）。
 *
 * 由应用根布局提供：0 = 底栏完全显示；正数 = 底栏已经滑下去多少，页面里贴底的按钮跟着挪多少。
 * 页面在布局阶段读这个值（`Modifier.offset { }`），底栏动画时不用重组也能逐帧跟上。
 */
val LocalBottomBarCollapseOffset = staticCompositionLocalOf<() -> Float> { { 0f } }
