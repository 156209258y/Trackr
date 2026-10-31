package com.trackr.ui.components.amap

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackr.data.remote.amap.AmapSdkManager
import com.trackr.util.SignatureUtils

/**
 * 高德 Key 与 SHA-1 签名配置对话框（第 11 步从路线规划页挪到「更多」页）。
 *
 * 高德开放平台的「Android 平台 Key」与「Web 服务 Key」互不通用，这里分两个输入框、各写各的槽，互不回落：
 * 前者决定地图能否出图，后者决定云端公交算路能否走 HTTP 接口。两个都没有时不显示「路线规划」页签（D2）；
 * 只有 Web Key 时有页签、能搜索和算路，但不画地图（Q18）。
 *
 * @param androidKey 用户填的 Android 槽（不含构建时注入的）
 * @param webKey 用户填的 Web 槽
 * @param onSaveAndroidKey 只在 Android 输入框改过时调用；传空串表示清空
 * @param onSaveWebKey 只在 Web 输入框改过时调用；传空串表示清空
 */
@Composable
fun AmapKeyDialog(
    androidKey: String?,
    webKey: String?,
    onSaveAndroidKey: (String) -> Unit,
    onSaveWebKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var inputKey by remember(androidKey) { mutableStateOf(androidKey.orEmpty()) }
    var inputWebKey by remember(webKey) { mutableStateOf(webKey.orEmpty()) }
    val currentSha1 = remember { SignatureUtils.getSigningCertificateSha1(context) }

    // 高德 Key 为 32 位字母数字组合，长度不符时直接拦下，省得用户以为“填了就该出图”
    fun isKeyFormatAcceptable(key: String): Boolean =
        key.isBlank() || key.length == 32 && key.all { it.isLetterOrDigit() }

    val isAndroidKeyFormatValid = isKeyFormatAcceptable(inputKey.trim())
    val isWebKeyFormatValid = isKeyFormatAcceptable(inputWebKey.trim())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "高德地图 Key 配置",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "本应用开源分发、不内置任何 Key，每位用户用自己的高德账号申请即可（个人开发者免费额度足够日常使用）。" +
                            "地图底图由「Android 平台 Key」鉴权，必须与下方包名 + SHA-1 完全一致；" +
                            "「Web 服务 Key」只用于云端公交算路，两者不能混填。" +
                            "两个都不填时不显示「路线规划」；只填 Web 服务 Key 时能搜地点、算路线，但不显示地图。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "申请步骤：① 复制包名 + SHA-1 → ② 控制台创建「Android 平台」Key，发布版 / 调试版 SHA-1 都填上 → ③ 把 Key 填到下面 → ④ 保存",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val text = "包名：${context.packageName}\nSHA-1：$currentSha1"
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "包名与 SHA-1 已复制", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("btn_copy_pkg_sha1")
                            ) {
                                Text("复制包名 + SHA-1", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = {
                                    runCatching {
                                        uriHandler.openUri("https://console.amap.com/dev/key/app")
                                    }.onFailure {
                                        Toast.makeText(context, "未找到可用浏览器，请手动访问 console.amap.com", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.testTag("btn_open_amap_console")
                            ) {
                                Text("打开高德控制台", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Package Name",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = context.packageName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(context.packageName))
                                        Toast.makeText(context, "包名已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制包名", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SHA-1 签名指纹",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = currentSha1.ifBlank { "正在读取…" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentSha1.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(currentSha1))
                                            Toast.makeText(context, "SHA-1 已复制", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = currentSha1.isNotBlank(),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制 SHA-1", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it.trim() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_amap_android_key"),
                        label = { Text("高德 Android Key（地图）") },
                        placeholder = { Text("粘贴 32 位 Android 平台 Key") },
                        isError = !isAndroidKeyFormatValid,
                        supportingText = {
                            Text(
                                text = when {
                                    !isAndroidKeyFormatValid -> "Key 应为 32 位字母数字，请检查是否粘贴完整"
                                    AmapSdkManager.isAndroidKeyConfigured(inputKey) -> "已配置，地图将使用该 Key 出图"
                                    else -> "未配置时路线规划不显示地图"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            if (inputKey.isNotBlank()) {
                                IconButton(onClick = { inputKey = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空 Android Key", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = inputWebKey,
                        onValueChange = { inputWebKey = it.trim() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_amap_web_key"),
                        label = { Text("高德 Web 服务 Key（可选）") },
                        placeholder = { Text("用于云端公交算路，不绑定包名") },
                        isError = !isWebKeyFormatValid,
                        supportingText = {
                            Text(
                                text = when {
                                    !isWebKeyFormatValid -> "Key 应为 32 位字母数字，请检查是否粘贴完整"
                                    AmapSdkManager.isWebServiceKeyConfigured(inputWebKey) -> "已配置"
                                    else -> "未配置时仅使用本地 + SDK 引擎算路"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            if (inputWebKey.isNotBlank()) {
                                IconButton(onClick = { inputWebKey = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清空 Web Key", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isAndroidKeyFormatValid && isWebKeyFormatValid,
                modifier = Modifier.testTag("btn_amap_key_save"),
                onClick = {
                    // 各写各的：只写改过的那一槽
                    val newAndroidKey = inputKey.trim()
                    val newWebKey = inputWebKey.trim()
                    if (newAndroidKey != androidKey.orEmpty()) onSaveAndroidKey(newAndroidKey)
                    if (newWebKey != webKey.orEmpty()) onSaveWebKey(newWebKey)
                    Toast.makeText(context, "Key 已保存", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("保存生效")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
