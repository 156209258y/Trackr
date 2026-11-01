@file:OptIn(ExperimentalMaterial3Api::class)

package com.trackr.ui.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_back
import com.trackr.core.designsystem.resources.ic_bus
import com.trackr.core.designsystem.resources.ic_check
import com.trackr.core.designsystem.resources.ic_warning
import org.jetbrains.compose.resources.vectorResource

/**
 * 关于页（第 25 步从 `androidMain` 的旧页改成 commonMain）。
 *
 * 「检查更新」只有 Android 有（`AboutUiState.canCheckUpdates`，§2.9）；iOS 走 App Store，这一行不显示。
 * 应用图标是设计系统里的公交图标，不读平台 Manifest，保证 iOS 也画得出来。
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onEvent: (AboutEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(text = "关于", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(AboutEvent.Back) }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_back),
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.ic_bus),
                                contentDescription = "追客",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "追客",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (state.versionName.isBlank()) "版本未知" else "版本 v${state.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.canCheckUpdates) {
                        UpdateRow(state = state, onClick = { onEvent(AboutEvent.CheckForUpdates) })
                        DottedDivider()
                    }

                    LinkRow(
                        title = "GitHub",
                        value = "5151561/Trackr",
                        onClick = { uriHandler.openUri(PROJECT_URL) }
                    )

                    DottedDivider()

                    TextRow(title = "开源协议", value = "MIT License")

                    DottedDivider()

                    TextBlock(
                        title = "数据声明",
                        body = "公交实时数据来源于公开网络接口，仅供出行参考。"
                    )

                    if (state.showLiveUpdateNote) {
                        DottedDivider()
                        TextBlock(
                            title = "实时通知与灵动胶囊支持",
                            body = "支持 Android 16 原生实时活动 (Live Updates)、小米澎湃OS 焦点通知（超级岛）与 OPPO ColorOS 流体云。" +
                                "若未在挖孔旁显示小胶囊，请前往系统设置为本应用开启「置顶通知 / 实时活动」或「允许焦点通知」。"
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Copyright (c) 2026 busonroad contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }

    UpdateDialogs(
        update = state.update,
        uriHandler = uriHandler,
        onRetry = { onEvent(AboutEvent.CheckForUpdates) },
        onDismiss = { onEvent(AboutEvent.DismissUpdateDialog) }
    )
}

/** 「检查新版本」那一行：检查中转圈，发现新版本给个标签 */
@Composable
private fun UpdateRow(state: AboutUiState, onClick: () -> Unit) {
    val checking = state.update is AboutUpdateUiState.Checking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !checking, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "检查新版本",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        when (val update = state.update) {
            is AboutUpdateUiState.Checking -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在检查...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AboutUpdateUiState.HasUpdate -> {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "发现新版本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            is AboutUpdateUiState.UpToDate -> Text(
                text = "已是最新",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            is AboutUpdateUiState.Error -> Text(
                text = "检查失败，点这里重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            AboutUpdateUiState.Idle -> Text(
                text = "点击检测",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TextRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TextBlock(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DottedDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

/** 更新检查的三种结果弹窗；Idle / Checking 不弹 */
@Composable
private fun UpdateDialogs(
    update: AboutUpdateUiState,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (update) {
        is AboutUpdateUiState.HasUpdate -> AlertDialog(
            onDismissRequest = { if (!update.isForce) onDismiss() },
            icon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_bus),
                    contentDescription = "发现新版本",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "发现新版本 v${update.updateInfo.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!update.updateInfo.publishTime.isNullOrBlank()) {
                        Text(
                            text = "发布时间: ${update.updateInfo.publishTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "更新内容：",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = update.updateInfo.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        uriHandler.openUri(update.updateInfo.downloadUrl)
                        if (!update.isForce) onDismiss()
                    }
                ) {
                    Text("前往下载")
                }
            },
            dismissButton = if (!update.isForce) {
                {
                    TextButton(onClick = onDismiss) { Text("稍后提醒") }
                }
            } else {
                null
            }
        )

        is AboutUpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_check),
                    contentDescription = "已是最新版本",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "已是最新版本",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "当前安装的版本 v${update.currentVersionName} 已是最新，暂无更新发布。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        )

        is AboutUpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_warning),
                    contentDescription = "检查更新失败",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "检查更新失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = update.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = onRetry) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        )

        AboutUpdateUiState.Idle, AboutUpdateUiState.Checking -> Unit
    }
}
