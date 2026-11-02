package com.trackr.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_bell
import com.trackr.feature.line.resources.Res as LineRes
import com.trackr.feature.line.resources.line_focus_body
import com.trackr.feature.line.resources.line_focus_device_xiaomi
import com.trackr.feature.line.resources.line_focus_fallback
import com.trackr.feature.line.resources.line_focus_go
import com.trackr.feature.line.resources.line_focus_later
import com.trackr.feature.line.resources.line_focus_permission_xiaomi
import com.trackr.feature.line.resources.line_focus_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/**
 * 系统实时活动（状态栏胶囊 / 流体云 / 焦点通知）权限未授权时的引导对话框
 */
@Composable
fun FocusPermissionGuideDialog(
    onDismissRequest: () -> Unit,
    onContinueWithoutSettings: () -> Unit,
    onGoToSettings: () -> Unit,
    title: String = stringResource(LineRes.string.line_focus_title),
    deviceHint: String = stringResource(LineRes.string.line_focus_device_xiaomi),
    permissionName: String = stringResource(LineRes.string.line_focus_permission_xiaomi)
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_bell),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = deviceHint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(LineRes.string.line_focus_body, permissionName),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(LineRes.string.line_focus_fallback),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(LineRes.string.line_focus_go))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onContinueWithoutSettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(LineRes.string.line_focus_later))
            }
        }
    )
}
