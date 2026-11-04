package com.trackr.ui.screens.favoritesedit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 拖动排序的位移计算（编辑收藏页）：拖动中「被拖那行跟手 + 中间的行各让一格」，松手后「整列按新顺序站好」。
 * 行高固定 100，方便看数。
 */
class ReorderShiftTest {

    private val row = 100f

    @Test
    fun draggedRow_followsTheFinger() {
        // 从第 1 行往下拖了 37px：被拖的那行就画在 37px，别的行还没跨过一整行，不动
        assertEquals(37f, dragShiftPx(index = 1, fromIndex = 1, toIndex = 1, dragging = true, dragOffsetPx = 37f, rowHeightPx = row))
        assertEquals(0f, dragShiftPx(index = 0, fromIndex = 1, toIndex = 1, dragging = false, dragOffsetPx = 37f, rowHeightPx = row))
        assertEquals(0f, dragShiftPx(index = 2, fromIndex = 1, toIndex = 1, dragging = false, dragOffsetPx = 37f, rowHeightPx = row))
    }

    @Test
    fun rowsBetweenStartAndTarget_shiftUp_whenDraggingDown() {
        // 第 1 行拖到第 3 行：原来 2、3 行各上移一格
        assertEquals(-row, dragShiftPx(2, fromIndex = 1, toIndex = 3, dragging = false, dragOffsetPx = 200f, rowHeightPx = row))
        assertEquals(-row, dragShiftPx(3, fromIndex = 1, toIndex = 3, dragging = false, dragOffsetPx = 200f, rowHeightPx = row))
        // 起止之外的行不动
        assertEquals(0f, dragShiftPx(0, fromIndex = 1, toIndex = 3, dragging = false, dragOffsetPx = 200f, rowHeightPx = row))
        assertEquals(0f, dragShiftPx(4, fromIndex = 1, toIndex = 3, dragging = false, dragOffsetPx = 200f, rowHeightPx = row))
    }

    @Test
    fun rowsBetweenStartAndTarget_shiftDown_whenDraggingUp() {
        // 第 3 行拖到第 1 行：原来 1、2 行各下移一格
        assertEquals(row, dragShiftPx(1, fromIndex = 3, toIndex = 1, dragging = false, dragOffsetPx = -200f, rowHeightPx = row))
        assertEquals(row, dragShiftPx(2, fromIndex = 3, toIndex = 1, dragging = false, dragOffsetPx = -200f, rowHeightPx = row))
        assertEquals(0f, dragShiftPx(0, fromIndex = 3, toIndex = 1, dragging = false, dragOffsetPx = -200f, rowHeightPx = row))
        assertEquals(0f, dragShiftPx(4, fromIndex = 3, toIndex = 1, dragging = false, dragOffsetPx = -200f, rowHeightPx = row))
    }

    @Test
    fun afterDrop_theWholeColumnStandsInTheNewOrder() {
        // 1 -> 3：被拖那行站到第 3 格（+2 格），2、3 行各上移一格
        assertEquals(2 * row, settledShiftPx(index = 1, fromIndex = 1, toIndex = 3, rowHeightPx = row))
        assertEquals(-row, settledShiftPx(index = 2, fromIndex = 1, toIndex = 3, rowHeightPx = row))
        assertEquals(-row, settledShiftPx(index = 3, fromIndex = 1, toIndex = 3, rowHeightPx = row))
        assertEquals(0f, settledShiftPx(index = 0, fromIndex = 1, toIndex = 3, rowHeightPx = row))

        // 3 -> 1：反过来
        assertEquals(-2 * row, settledShiftPx(index = 3, fromIndex = 3, toIndex = 1, rowHeightPx = row))
        assertEquals(row, settledShiftPx(index = 1, fromIndex = 3, toIndex = 1, rowHeightPx = row))
        assertEquals(row, settledShiftPx(index = 2, fromIndex = 3, toIndex = 1, rowHeightPx = row))
    }

    @Test
    fun droppedInPlace_nothingMoves() {
        for (index in 0..3) {
            assertEquals(0f, settledShiftPx(index, fromIndex = 2, toIndex = 2, rowHeightPx = row))
            assertEquals(0f, dragShiftPx(index, fromIndex = 2, toIndex = 2, dragging = false, dragOffsetPx = 0f, rowHeightPx = row))
        }
    }

    // ---- 边缘自动翻页 ----

    @Test
    fun autoScroll_isStillInTheMiddle() {
        // 视口 0..800、边缘 72：手指在 400 就是不动
        assertEquals(0f, edgeScrollStepPx(400f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f))
        assertEquals(0f, edgeScrollStepPx(728f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f))
        assertEquals(0f, edgeScrollStepPx(72f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f))
    }

    @Test
    fun autoScroll_scrollsDownNearTheBottomEdge() {
        // 越靠下越快：刚进边缘区是一点点，贴到底就是满速
        val justInside = edgeScrollStepPx(730f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f)
        val atEdge = edgeScrollStepPx(800f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f)
        val outside = edgeScrollStepPx(900f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f)
        assertTrue(justInside > 0f && justInside < 15f)
        assertEquals(15f, atEdge)
        // 出了视口也封顶，别越拖越快
        assertEquals(15f, outside)
    }

    @Test
    fun autoScroll_scrollsUpNearTheTopEdge() {
        val justInside = edgeScrollStepPx(60f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f)
        val atEdge = edgeScrollStepPx(0f, viewportTop = 0f, viewportBottom = 800f, edgePx = 72f, maxStepPx = 15f)
        assertTrue(justInside < 0f && justInside > -15f)
        assertEquals(-15f, atEdge)
    }

    @Test
    fun autoScroll_doesNothingBeforeTheListIsMeasured() {
        // 还没布局（viewport 是空的）时别乱滚
        assertEquals(0f, edgeScrollStepPx(400f, viewportTop = 0f, viewportBottom = 0f, edgePx = 72f, maxStepPx = 15f))
        assertEquals(0f, edgeScrollStepPx(400f, viewportTop = 100f, viewportBottom = 800f, edgePx = 0f, maxStepPx = 15f))
    }
}
