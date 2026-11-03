package com.trackr.core.network.provider

import com.trackr.core.model.LineOperatingState
import com.trackr.core.network.remote.mygolbs.MygolbsApiClient
import com.trackr.core.network.testing.mockHttpClient
import com.trackr.core.network.testing.mockResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 掌上公交的运营时段文案：末班之后不再跟着上游说「等待发车」。
 */
class MygolbsServiceHoursTest {

    /** 北京时间 2026-11-03 23:50 */
    private val afterLastClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_793_721_000_000L)
    }

    /** 北京时间 2026-11-03 05:00 */
    private val beforeFirstClock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_793_653_200_000L)
    }

    private val stopLinesJson = """
    {
      "status": 1,
      "data": [
        {
          "lineName": "414路",
          "upperOrDown": "1",
          "from": "A",
          "to": "B",
          "neartext": "等待发车",
          "routeBean": {
            "hasReal": 1,
            "suTime": "06:30",
            "euTime": "22:00",
            "seutimes": [{"first": "06:30", "last": "22:00"}],
            "sedtimes": [{"first": "07:00", "last": "22:00"}]
          }
        },
        {
          "lineName": "夜1",
          "upperOrDown": "2",
          "from": "C",
          "to": "D",
          "neartext": "等待发车",
          "routeBean": {
            "hasReal": 1,
            "seutimes": [{"first": "04:30", "last": "05:40"}, {"first": "22:40", "last": "01:30"}],
            "sedtimes": [{"first": "04:30", "last": "05:40"}, {"first": "22:40", "last": "01:30"}]
          }
        }
      ]
    }
    """.trimIndent()

    private val lineStaticJson = """
    {
      "status": 1,
      "routeId": 17,
      "firstLast": [{"first": "06:45", "last": "21:35"}],
      "commonts": "票价2元",
      "data": [
        {"stationName": "A站", "stationOrder": 1, "station_lat": 22.0, "station_lng": 110.0, "stationsStatus": 1},
        {"stationName": "B站", "stationOrder": 2, "station_lat": 22.001, "station_lng": 110.001, "stationsStatus": 1}
      ]
    }
    """.trimIndent()

    private val lineRealtimeJson = """
    {"status": 1, "runState": 0, "hasReal": 1, "list": [], "routeOnStationRTimeInfoList": []}
    """.trimIndent()

    private fun client(clock: Clock, stopJson: String = stopLinesJson) = MygolbsApiClient(
        mockHttpClient { request ->
            if (request.body.contains("CMD=105")) {
                mockResponse(request, stopJson)
            } else {
                mockResponse(request, """{"status":1,"data":[]}""")
            }
        },
        wallClock = clock
    )

    @Test
    fun afterLastBus_waitingBecomesEnded() = runBlocking {
        val stop = client(afterLastClock).getStopLines("玉林市", "市政广场")
        val normal = stop.lines.first { it.line.name == "414路" }
        assertEquals("末班已过", normal.statusText)
        assertEquals("06:30", normal.line.firstTime)
        assertEquals("22:00", normal.line.lastTime)

        // 夜班跨零点，23:50 还在运营，保留上游文案
        val night = stop.lines.first { it.line.name == "夜1" }
        assertEquals("等待发车", night.statusText)
        assertEquals("04:30", night.line.firstTime)
        assertEquals("01:30", night.line.lastTime)
    }

    @Test
    fun beforeFirstBus_keepsWaiting() = runBlocking {
        val stop = client(beforeFirstClock).getStopLines("玉林市", "市政广场")
        val normal = stop.lines.first { it.line.name == "414路" }
        assertEquals("等待发车", normal.statusText)
    }

    @Test
    fun lineDetail_afterLastBus_isEnded() = runBlocking {
        val detail = detailClient(afterLastClock).getLineDetail("mg:450900:玉林市:17路:2", targetOrder = 2)
        assertEquals(LineOperatingState.ENDED, detail.operatingState)
        assertEquals("末班已过", detail.statusDescription)
    }

    @Test
    fun lineDetail_beforeFirstBus_isWaiting() = runBlocking {
        val detail = detailClient(beforeFirstClock).getLineDetail("mg:450900:玉林市:17路:2", targetOrder = 2)
        assertEquals(LineOperatingState.WAITING, detail.operatingState)
        assertEquals("等待发车", detail.statusDescription)
    }

    private fun detailClient(clock: Clock) = MygolbsApiClient(
        mockHttpClient { request ->
            val body = request.body
            when {
                body.contains("CMD=103") -> mockResponse(request, lineStaticJson)
                body.contains("CMD=104") -> mockResponse(request, lineRealtimeJson)
                else -> mockResponse(request, """{"status":1,"data":[]}""")
            }
        },
        wallClock = clock
    )

    @Test
    fun searchLines_takesFirstLastByDirection() = runBlocking {
        val json = """
        {
          "status": 1,
          "buslines": [
            {
              "lineName": "17路",
              "upperOrDown": "2",
              "from": "A",
              "to": "B",
              "comments": "2元",
              "routeBean": {
                "routeType": "1",
                "suTime": "06:30",
                "euTime": "18:00",
                "sdTime": "06:35",
                "edTime": "19:05",
                "seutimes": [{"first": "06:30", "last": "18:00"}],
                "sedtimes": [{"first": "07:00", "last": "19:00"}]
              }
            }
          ]
        }
        """.trimIndent()
        val client = MygolbsApiClient(mockHttpClient { request -> mockResponse(request, json) })

        val dirs = client.searchLines(cityCode = "450900", cityName = "玉林市", keyword = "17").single().directions

        val up = dirs.first { it.direction == 0 }
        assertEquals("06:30", up.firstTime)
        assertEquals("18:00", up.lastTime)
        val down = dirs.first { it.direction == 1 }
        assertEquals("07:00", down.firstTime)
        assertEquals("19:00", down.lastTime)
    }
}
