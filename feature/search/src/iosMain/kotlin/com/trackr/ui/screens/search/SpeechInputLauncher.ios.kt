package com.trackr.ui.screens.search

import androidx.compose.runtime.Composable
import com.trackr.core.domain.platform.SpeechInput

/** iOS 还没有语音输入（第 36 步），话筒不显示 */
@Composable
actual fun rememberSpeechInput(): SpeechInput? = null
