package com.trackr.ui.screens.stopdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trackr.core.designsystem.component.TrackrBottomSheet
import com.trackr.ui.icons.TrackrIcons

/**
 * 「上了哪辆？」（§2.6 第 ④ 条）：点「我上车了」时不同线的车同时在站、定位又分不清才弹。
 * 只依赖 [BoardChoiceUi]，第 18 步可以直接接成导航弹层（`ScreenKey.ChooseBoardedBus`）。
 *
 * @param onChoose 选了哪辆车的 busId；null =「还没上」
 * @param onDismiss 划掉 / 返回，不选
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseBoardedBusSheet(
    choice: BoardChoiceUi,
    onChoose: (busId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    TrackrBottomSheet(onDismissRequest = onDismiss) {
        ChooseBoardedBusContent(choice = choice, onChoose = onChoose)
    }
}

/** 弹层内容（测试直接渲染它） */
@Composable
fun ChooseBoardedBusContent(
    choice: BoardChoiceUi,
    onChoose: (busId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text(
            text = "上了哪辆？",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = choice.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                choice.options.forEachIndexed { index, option ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onChoose(option.busId) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineBadge(option.lineName)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (option.endStop.isNotBlank()) {
                                Text(
                                    text = "→ ${option.endStop}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (option.plate.isNotBlank()) {
                                Text(
                                    text = option.plate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = TrackrIcons.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { onChoose(null) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("还没上", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
