@file:OptIn(ExperimentalMaterial3Api::class)

package com.trackr.ui.screens.favoritesedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.action_back
import com.trackr.core.designsystem.resources.action_cancel
import com.trackr.core.designsystem.resources.action_delete
import com.trackr.core.designsystem.resources.action_done
import com.trackr.core.designsystem.resources.action_save
import com.trackr.core.designsystem.resources.group_none
import com.trackr.core.designsystem.resources.ic_back
import com.trackr.core.designsystem.resources.ic_delete
import com.trackr.core.designsystem.resources.ic_drag
import com.trackr.core.designsystem.resources.ic_edit
import com.trackr.core.designsystem.resources.ic_plus
import com.trackr.core.designsystem.resources.ic_route
import com.trackr.core.model.FavoriteGroup
import com.trackr.core.model.TransitGroup
import com.trackr.core.navigation.TrackrNavigator
import com.trackr.core.navigation.ScreenKey
import com.trackr.feature.favorites.resources.Res as FavoritesRes
import com.trackr.feature.favorites.resources.fav_edit_create
import com.trackr.feature.favorites.resources.fav_edit_delete_favorite
import com.trackr.feature.favorites.resources.fav_edit_delete_group_body
import com.trackr.feature.favorites.resources.fav_edit_delete_group_empty
import com.trackr.feature.favorites.resources.fav_edit_delete_group_title
import com.trackr.feature.favorites.resources.fav_edit_delete_route_body
import com.trackr.feature.favorites.resources.fav_edit_delete_route_title
import com.trackr.feature.favorites.resources.fav_edit_delete_this_group
import com.trackr.feature.favorites.resources.fav_edit_drag
import com.trackr.feature.favorites.resources.fav_edit_empty
import com.trackr.feature.favorites.resources.fav_edit_group_actions
import com.trackr.feature.favorites.resources.fav_edit_group_dialog_title
import com.trackr.feature.favorites.resources.fav_edit_group_name
import com.trackr.feature.favorites.resources.fav_edit_group_name_hint
import com.trackr.feature.favorites.resources.fav_edit_hint
import com.trackr.feature.favorites.resources.fav_edit_new_group
import com.trackr.feature.favorites.resources.fav_edit_no_routes
import com.trackr.feature.favorites.resources.fav_edit_route_count
import com.trackr.feature.favorites.resources.fav_edit_section_favorites
import com.trackr.feature.favorites.resources.fav_edit_section_groups
import com.trackr.feature.favorites.resources.fav_edit_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** 编辑收藏页的动作 */
sealed interface FavoritesEditEvent {
    data object Back : FavoritesEditEvent
    data object Done : FavoritesEditEvent
    data class OpenRoute(val groupId: String) : FavoritesEditEvent
    data class CreateGroup(val name: String) : FavoritesEditEvent
    data class RenameGroup(val groupId: String, val name: String) : FavoritesEditEvent
    data class DeleteGroup(val groupId: String) : FavoritesEditEvent
    data class ReorderGroups(val ids: List<String>) : FavoritesEditEvent
    data class DeleteRoute(val groupId: String) : FavoritesEditEvent
    data class ReorderRoutes(val ids: List<String>) : FavoritesEditEvent
}

/**
 * 编辑收藏（v3 第 22 步）：分组和收藏的路线都在这一页管，拖动排序、改名、删除。
 * 删分组时里面的路线变成「不分组」。点一条路线进路线详情。
 */
@Composable
fun FavoritesEditScreen(
    groups: List<TransitGroup>,
    favoriteGroups: List<FavoriteGroup>,
    onEvent: (FavoritesEditEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<FavoriteGroup?>(null) }
    var deletingRoute by remember { mutableStateOf<TransitGroup?>(null) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(FavoritesRes.string.fav_edit_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(FavoritesEditEvent.Back) }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.ic_back),
                            contentDescription = stringResource(Res.string.action_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onEvent(FavoritesEditEvent.Done) }) {
                        Text(stringResource(Res.string.action_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "groups_title") {
                SectionTitle(stringResource(FavoritesRes.string.fav_edit_section_groups))
            }

            item(key = "groups_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column {
                        ReorderableColumn(
                            ids = favoriteGroups.map { it.id },
                            onReorder = { onEvent(FavoritesEditEvent.ReorderGroups(it)) }
                        ) { id, handle ->
                            val group = favoriteGroups.firstOrNull { it.id == id } ?: return@ReorderableColumn
                            GroupRow(
                                group = group,
                                count = groups.count { it.customGroupId == group.id },
                                handle = handle,
                                onEdit = { editingGroup = group }
                            )
                        }

                        if (favoriteGroups.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateGroup = true }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("btn_create_favorite_group"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.ic_plus),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(FavoritesRes.string.fav_edit_new_group),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item(key = "routes_title") {
                SectionTitle(stringResource(FavoritesRes.string.fav_edit_section_favorites))
            }

            if (groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(FavoritesRes.string.fav_edit_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            } else {
                item(key = "routes_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        ReorderableColumn(
                            ids = groups.map { it.id },
                            onReorder = { onEvent(FavoritesEditEvent.ReorderRoutes(it)) }
                        ) { id, handle ->
                            val group = groups.firstOrNull { it.id == id } ?: return@ReorderableColumn
                            RouteRow(
                                group = group,
                                groupName = favoriteGroups.firstOrNull { it.id == group.customGroupId }?.name,
                                handle = handle,
                                onClick = { onEvent(FavoritesEditEvent.OpenRoute(group.id)) },
                                onDelete = { deletingRoute = group }
                            )
                        }
                    }
                }
            }

            item(key = "hint") {
                Text(
                    text = stringResource(FavoritesRes.string.fav_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (showCreateGroup) {
        NameDialog(
            title = stringResource(FavoritesRes.string.fav_edit_new_group),
            initial = "",
            confirmLabel = stringResource(FavoritesRes.string.fav_edit_create),
            onDismiss = { showCreateGroup = false },
            onConfirm = { name ->
                showCreateGroup = false
                onEvent(FavoritesEditEvent.CreateGroup(name))
            }
        )
    }

    editingGroup?.let { group ->
        EditGroupDialog(
            group = group,
            count = groups.count { it.customGroupId == group.id },
            onDismiss = { editingGroup = null },
            onRename = { name ->
                editingGroup = null
                onEvent(FavoritesEditEvent.RenameGroup(group.id, name))
            },
            onDelete = {
                editingGroup = null
                onEvent(FavoritesEditEvent.DeleteGroup(group.id))
            }
        )
    }

    deletingRoute?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingRoute = null },
            title = { Text(stringResource(FavoritesRes.string.fav_edit_delete_route_title)) },
            text = { Text(stringResource(FavoritesRes.string.fav_edit_delete_route_body, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingRoute = null
                    onEvent(FavoritesEditEvent.DeleteRoute(group.id))
                }) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoute = null }) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun GroupRow(
    group: FavoriteGroup,
    count: Int,
    handle: Modifier,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandle(handle)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (count == 0) stringResource(FavoritesRes.string.fav_edit_no_routes) else stringResource(FavoritesRes.string.fav_edit_route_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.testTag("btn_edit_group_${group.id}")) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_edit),
                contentDescription = stringResource(FavoritesRes.string.fav_edit_group_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RouteRow(
    group: TransitGroup,
    groupName: String?,
    handle: Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("route_row_${group.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DragHandle(handle)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = vectorResource(Res.drawable.ic_route),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append("${group.originStopName} → ${group.destinationStopName}")
                    append(" · ")
                    append(groupName ?: stringResource(Res.string.group_none))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("btn_delete_route_${group.id}")) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_delete),
                contentDescription = stringResource(FavoritesRes.string.fav_edit_delete_favorite),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 拖动把手：长按后上下拖，松手时换算成目标位置 */
@Composable
private fun DragHandle(modifier: Modifier) {
    Icon(
        imageVector = vectorResource(Res.drawable.ic_drag),
        contentDescription = stringResource(FavoritesRes.string.fav_edit_drag),
        tint = MaterialTheme.colorScheme.outlineVariant,
        modifier = modifier
            .size(28.dp)
            .alpha(0.9f)
    )
}

/**
 * 一列可拖动排序的行。拖动时只移动被拖的那一行，松手后回调新顺序。
 * 行高取第一行的高度（这一页的行都是单行 / 两行小字，高度一致）。
 */
@Composable
private fun ReorderableColumn(
    ids: List<String>,
    onReorder: (List<String>) -> Unit,
    row: @Composable (id: String, handle: Modifier) -> Unit
) {
    val density = LocalDensity.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ids.forEachIndexed { index, id ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        if (index == 0 && it.height > 0) rowHeightPx = it.height.toFloat()
                    }
                    .graphicsLayer { translationY = if (draggingId == id) offset else 0f }
                    .zIndex(if (draggingId == id) 1f else 0f)
            ) {
                row(
                    id,
                    Modifier.pointerInput(ids) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = id; offset = 0f },
                            onDragEnd = {
                                val from = ids.indexOf(id)
                                val deltaRows = (offset / rowHeightPx).toInt()
                                val to = (from + deltaRows).coerceIn(0, ids.lastIndex)
                                if (from != to && from >= 0) {
                                    val next = ids.toMutableList()
                                    next.add(to, next.removeAt(from))
                                    onReorder(next)
                                }
                                draggingId = null
                                offset = 0f
                            },
                            onDragCancel = { draggingId = null; offset = 0f },
                            onDrag = { change, dragAmount: Offset ->
                                change.consume()
                                offset += dragAmount.y
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(FavoritesRes.string.fav_edit_group_name)) },
                placeholder = { Text(stringResource(FavoritesRes.string.fav_edit_group_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_group_name")
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}

@Composable
private fun EditGroupDialog(
    group: FavoriteGroup,
    count: Int,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(FavoritesRes.string.fav_edit_delete_group_title, group.name)) },
            text = {
                Text(
                    if (count == 0) stringResource(FavoritesRes.string.fav_edit_delete_group_empty)
                    else stringResource(FavoritesRes.string.fav_edit_delete_group_body, count)
                )
            },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(FavoritesRes.string.fav_edit_group_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(FavoritesRes.string.fav_edit_group_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text(stringResource(FavoritesRes.string.fav_edit_delete_this_group), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    }
}

/** 编辑收藏 Route：读状态、翻译事件、跳路线详情 */
@Composable
fun FavoritesEditRoute(
    viewModel: FavoritesEditViewModel,
    navigator: TrackrNavigator,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val favoriteGroups by viewModel.favoriteGroups.collectAsStateWithLifecycle()
    val back = dropUnlessResumed { navigator.popChildPage() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val openRoute: (String) -> Unit = { id ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            navigator.open(ScreenKey.RouteDetail(id))
        }
    }

    FavoritesEditScreen(
        groups = groups,
        favoriteGroups = favoriteGroups,
        onEvent = { event ->
            when (event) {
                FavoritesEditEvent.Back, FavoritesEditEvent.Done -> back()
                is FavoritesEditEvent.OpenRoute -> openRoute(event.groupId)
                is FavoritesEditEvent.CreateGroup -> viewModel.createFavoriteGroup(event.name)
                is FavoritesEditEvent.RenameGroup -> viewModel.renameFavoriteGroup(event.groupId, event.name)
                is FavoritesEditEvent.DeleteGroup -> viewModel.deleteFavoriteGroup(event.groupId)
                is FavoritesEditEvent.ReorderGroups -> viewModel.reorderFavoriteGroups(event.ids)
                is FavoritesEditEvent.DeleteRoute -> viewModel.deleteGroup(event.groupId)
                is FavoritesEditEvent.ReorderRoutes -> viewModel.reorderGroups(event.ids)
            }
        },
        modifier = modifier
    )
}
