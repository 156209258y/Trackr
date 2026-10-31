package com.trackr.ui.screens.routeplan.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_plus
import com.trackr.core.model.AmapTransitSchemeResult
import com.trackr.core.model.FavoriteGroup
import org.jetbrains.compose.resources.vectorResource

/**
 * 存进收藏：路线名 + 分组（「全部」= 不指定，回落到收藏页当前选中的分组）+ 新建分组。
 * 第 24 步从旧页面原样搬进 commonMain；v3 的收藏弹层（只问名字和分组）是第 22 步。
 */
@Composable
internal fun SaveFavoriteDialog(
    scheme: AmapTransitSchemeResult,
    favoriteGroups: List<FavoriteGroup>,
    onDismiss: () -> Unit,
    onConfirmSave: (groupId: String?, name: String) -> Unit,
    onCreateNewGroup: (String) -> Unit
) {
    var groupName by remember(scheme) { mutableStateOf(scheme.group.name) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showNewGroupInput by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "存入收藏",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "方案：${scheme.title} (约 ${scheme.durationMinutes} 分钟)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("路线名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_favorite_name")
                )

                Text(
                    text = "选择所属收藏分组：",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGroupId == null,
                        onClick = { selectedGroupId = null },
                        label = { Text("全部") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    favoriteGroups.forEach { group ->
                        FilterChip(
                            selected = selectedGroupId == group.id,
                            onClick = { selectedGroupId = group.id },
                            label = { Text(group.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                if (!showNewGroupInput) {
                    TextButton(
                        onClick = { showNewGroupInput = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(imageVector = vectorResource(Res.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建自定义分组")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            placeholder = { Text("输入新分组名 (如: 周末出行)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateNewGroup(newGroupName.trim())
                                    newGroupName = ""
                                    showNewGroupInput = false
                                }
                            }
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmSave(selectedGroupId, groupName.trim()) },
                modifier = Modifier.testTag("btn_confirm_save_favorite")
            ) {
                Text("确认存入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
