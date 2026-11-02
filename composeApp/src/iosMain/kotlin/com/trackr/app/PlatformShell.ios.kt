package com.trackr.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.designsystem.resources.Res
import com.trackr.core.designsystem.resources.ic_bus
import com.trackr.core.designsystem.resources.ic_person
import com.trackr.core.designsystem.resources.ic_route
import com.trackr.core.navigation.Navigator
import com.trackr.core.navigation.ScreenKey
import org.jetbrains.compose.resources.vectorResource

@Composable
internal actual fun PlatformHost(enabled: Boolean, content: @Composable () -> Unit) = content()

@Composable
internal actual fun PermissionOnboarding(afterOnboarding: @Composable () -> Unit) = afterOnboarding()

@Composable
internal actual fun bottomTabIcon(key: ScreenKey): ImageVector = when (key) {
    ScreenKey.Home -> vectorResource(Res.drawable.ic_bus)
    ScreenKey.RoutePlan -> vectorResource(Res.drawable.ic_route)
    else -> vectorResource(Res.drawable.ic_person)
}
