package com.trackr.feature.me

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.trackr.core.navigation.Navigator

/** 注册「更多」「关于」两页的导航 entry。Android 接旧页面；iOS 先放空壳页 */
expect fun EntryProviderScope<NavKey>.meEntries(navigator: Navigator)
