package com.trackr.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 所有导航动作都走这里（§3.3）。纯 Kotlin，只改 [NavigationState]，`NavigatorTest` 直接测（修 N7）。
 */
class Navigator(private val state: NavigationState) {

    private val _reselect = MutableSharedFlow<ScreenKey>(extraBufferCapacity = 1)

    /** 再点当前页签时发出该页签（栈已回到根）；以后给列表回顶用。 */
    val reselect: SharedFlow<ScreenKey> = _reselect.asSharedFlow()

    /** 点底栏：切到别的页签保留各自的栈；再点当前页签则回到它的根页。 */
    fun selectTab(tab: ScreenKey) {
        require(tab in state.stacks) { "$tab 不是顶层页签" }
        if (tab == state.topLevel) {
            state.currentStack.popToRoot()
            _reselect.tryEmit(tab)
        } else {
            state.topLevel = tab
        }
    }

    /** 在当前页签的栈上打开 [key]；栈顶已经是它就不重复压栈（保留旧行为，防连点）。 */
    fun open(key: ScreenKey) {
        state.currentStack.pushIfNotOnTop(key)
    }

    /**
     * 切到 [tab]（不回根）再在它的栈上打开 [key]。小组件 / 通知跳线路详情用，栈顶去重防重放；
     * 第 18 步换成 `applyDeepLink`（合成返回栈）。
     */
    fun openInTab(tab: ScreenKey, key: ScreenKey) {
        require(tab in state.stacks) { "$tab 不是顶层页签" }
        state.topLevel = tab
        state.stacks.getValue(tab).pushIfNotOnTop(key)
    }

    /**
     * 返回：当前栈有子页就弹一页；非起始页签在根页就回起始页签；起始页签根页返回 false，交给系统退出。
     */
    fun back(): Boolean {
        val stack = state.currentStack
        return when {
            stack.size > 1 -> {
                stack.removeLastOrNull()
                true
            }
            state.topLevel != state.start -> {
                state.topLevel = state.start
                true
            }
            else -> false
        }
    }

    /** 页签集合变化（D2：没 Key 隐藏「路线」）：当前页签不在了就清空它的栈并回起始页签。 */
    fun onTabsChanged(visible: Set<ScreenKey>) {
        if (state.topLevel !in visible) {
            state.currentStack.popToRoot()
            state.topLevel = state.start
        }
    }

    private fun MutableList<NavKey>.pushIfNotOnTop(key: ScreenKey) {
        if (lastOrNull() != key) add(key)
    }

    private fun MutableList<NavKey>.popToRoot() {
        while (size > 1) removeLastOrNull()
    }
}
