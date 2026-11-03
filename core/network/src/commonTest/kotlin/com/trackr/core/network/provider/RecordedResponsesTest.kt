package com.trackr.core.network.provider

import com.trackr.core.network.remote.chelaile.ChelaileApiClient
import com.trackr.core.network.remote.mygolbs.MygolbsApiClient
import com.trackr.core.network.testing.MockReply
import com.trackr.core.network.testing.MockRequest
import com.trackr.core.network.testing.mockHttpClient
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用录好的真实响应（[RecordedResponses]）走一遍 Ktor 客户端 + 解析（重构计划第 8 步、R17）。
 * 期望值是录制时同一套代码在真实网络上解析出来的结果；这里再核对一遍发出去的请求长什么样。
 */
class RecordedResponsesTest {

    private val lock = SynchronizedObject()
    private val requests = mutableListOf<MockRequest>()

    private fun chelaileHttp() = mockHttpClient { request ->
        synchronized(lock) { requests += request }
        val body = when {
            request.encodedPath.endsWith("stop!encryptedHomePage.action") -> RecordedResponses.CHELAILE_HOME_PAGE
            request.encodedPath.endsWith("line!encryptedLineDetail.action") -> RecordedResponses.CHELAILE_LINE_DETAIL
            request.encodedPath.endsWith("line!encryptedBusDetail.action") -> RecordedResponses.CHELAILE_BUS_DETAIL
            request.encodedPath.endsWith("stop!encryptedPhyStnDetail.action") -> RecordedResponses.CHELAILE_PHY_STN_DETAIL
            request.encodedPath.endsWith("line!preStartTimetableNew.action") -> RecordedResponses.CHELAILE_TIMETABLE
            else -> error("unexpected ${request.url}")
        }
        MockReply(body)
    }

    private fun mygolbsHttp() = mockHttpClient { request ->
        synchronized(lock) { requests += request }
        val cmd = Regex("CMD=(\\d+)").find(request.body)?.groupValues?.get(1)
        val body = when (cmd) {
            "106" -> RecordedResponses.MYGOLBS_CMD106
            "105" -> RecordedResponses.MYGOLBS_CMD105
            "103" -> RecordedResponses.MYGOLBS_CMD103
            "104" -> RecordedResponses.MYGOLBS_CMD104
            else -> error("unexpected CMD=$cmd")
        }
        MockReply(body)
    }

    @Test
    fun chelaile_nearbyStops_fromRecordedHomePage() = runBlocking {
        val stops = ChelaileApiClient(chelaileHttp()).getNearbyStops("034", 31.2304, 121.4737, 4)

        assertEquals(listOf("西藏中路", "西藏南路宁海东路", "延安东路西藏中路", "人民广场（武胜路）"), stops.map { it.name })
        val first = stops.first()
        assertEquals("2868ecd4156f42b4b4cd38bfbd5dcb00", first.physicalStId)
        assertEquals("21283603183", first.lines.first().line.lineId)
        assertEquals(2, first.lines.first().targetOrder)
        // 首页接口不给站点坐标，只给服务端算好的距离
        assertEquals(89, first.distanceMeters)

        // 发出去的请求：带小程序请求头和签名，GET 上不再带 Content-Type（Ktor 不允许，原来也没有请求体）
        val request = requests.single()
        assertEquals("web.chelaile.net.cn", request.header("Host"))
        assertTrue(request.header("Referer").orEmpty().startsWith("https://servicewechat.com/"), request.header("Referer"))
        assertNull(request.header("Content-Type"))
        assertEquals("034", request.queryParameter("cityId"))
        assertEquals(32, request.queryParameter("cryptoSign")?.length)
    }

    @Test
    fun chelaile_lineDetail_withBusDetail_fromRecorded() = runBlocking {
        val detail = ChelaileApiClient(chelaileHttp())
            .getLineDetail("034", "21283603183", targetOrder = 2, stationId = "021-15232")

        assertEquals("71", detail.line.name)
        assertEquals(24, detail.stops.size)
        assertEquals((1..24).toList(), detail.stops.map { it.order })
        assertTrue(detail.stops.all { it.name.isNotBlank() })
        assertEquals(0, detail.buses.size)
        assertEquals(2, detail.targetOrder)
        // 线路详情 + 到站预测各一次
        assertEquals(
            listOf("line!encryptedLineDetail.action", "line!encryptedBusDetail.action"),
            requests.map { it.encodedPath.substringAfterLast('/') }
        )
    }

    @Test
    fun chelaile_stopDetail_fromRecorded() = runBlocking {
        val stations = ChelaileApiClient(chelaileHttp()).getStopDetail(
            cityId = "034",
            physicalStId = "2868ecd4156f42b4b4cd38bfbd5dcb00",
            namesakeStId = "c2bd23ef55aa0701976b156087099c9c",
            stationId = "021-15232"
        )

        assertEquals(2, stations.size)
        assertEquals(1, stations.first().lines.size)
        assertTrue(stations.all { it.name.isNotBlank() })
    }

    @Test
    fun chelaile_timetable_fromRecorded() = runBlocking {
        val timetable = ChelaileApiClient(chelaileHttp())
            .getTimetable("034", "21283603183", "r95817", 0, atTimeHHMM = "12:00")

        assertEquals(0, timetable.todayDepartures.size)
        assertEquals(169, timetable.yesterdayDepartures.size)
        assertEquals(timetable.yesterdayDepartures.sorted(), timetable.yesterdayDepartures)
        assertEquals("昨日实际班次", timetable.nextDepartureSource)
        assertTrue(timetable.nextDeparture.orEmpty() > "12:00")
    }

    @Test
    fun mygolbs_nearbyStops_fromRecorded() = runBlocking {
        val stops = MygolbsApiClient(mygolbsHttp()).getNearbyStops("玉林", 22.6544, 110.1810, 1)

        assertEquals(listOf("市政广场"), stops.map { it.name })
        assertEquals(
            listOf("玉林北站定制夜班车", "G02路", "17路", "玉林旅游客运站至北流成南站公交线路"),
            stops.single().lines.map { it.line.name }
        )
        // CMD=105 的 stationOrder 恒为 0；站序取 stationID + 1（12/16/10/3 -> 13/17/11/4），和 CMD=103 站表一致
        assertEquals(listOf(13, 17, 11, 4), stops.single().lines.map { it.targetOrder })

        // 表单 POST：Content-Type 和原来 OkHttp 时一样不带 charset
        val first = requests.first()
        assertEquals("application/x-www-form-urlencoded", first.contentType)
        assertEquals("https://h5.mygolbs.com", first.header("Origin"))
        assertTrue(first.body.startsWith("CMD=106&CITYNAME=%E7%8E%89%E6%9E%97%E5%B8%82"), first.body)
    }

    @Test
    fun mygolbs_lineDetail_fromRecorded() = runBlocking {
        val detail = MygolbsApiClient(mygolbsHttp()).getLineDetail("mg:0:玉林市:玉林北站定制夜班车:2")

        assertEquals("玉林北站定制夜班车", detail.line.name)
        assertEquals("8元", detail.line.price)
        assertEquals(27, detail.stops.size)
        assertEquals("汽车总站（二医院）", detail.stops.first().name)
        assertEquals("玉林北站", detail.stops.last().name)
        assertEquals(0, detail.buses.size)
        assertEquals(2, requests.size)
    }
}
