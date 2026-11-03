package com.trackr.core.network.provider

import com.trackr.core.model.TransitCity
import com.trackr.core.network.remote.mygolbs.MygolbsIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MygolbsIdsTest {

    @Test
    fun lineIdRoundTrip() {
        val id = MygolbsIds.lineId("450900", "玉林市", "1路", "2")
        assertEquals("mg:450900:玉林市:1路:2", id)

        val ref = MygolbsIds.parseLineId(id)
        assertEquals("450900", ref?.cityCode)
        assertEquals("玉林市", ref?.cityName)
        assertEquals("1路", ref?.lineName)
        assertEquals("2", ref?.direction)
        assertEquals(2, ref?.directionCode)
        assertEquals("1", ref?.reversed()?.direction)
    }

    @Test
    fun parseKeepsColonInsideLineName() {
        val ref = MygolbsIds.parseLineId("mg:450900:玉林市:旅游1线:夜班:2")
        assertEquals("旅游1线:夜班", ref?.lineName)
        assertEquals("2", ref?.direction)
    }

    @Test
    fun parseRejectsNonMygolbsOrMalformed() {
        assertNull(MygolbsIds.parseLineId("cl_123"))
        assertNull(MygolbsIds.parseLineId("mg:450900:玉林市"))
        assertNull(MygolbsIds.parseLineId("mg:450900:玉林市:1路:abc"))
    }

    @Test
    fun lineRefUsesCurrentCityCodeAndName() {
        val city = TransitCity(cityId = "", cityName = "玉林", mygolbsCityCode = "450900")
        val ref = MygolbsIds.lineRef("mg:0:玉林:17路:1", city)
        assertEquals("450900", ref?.cityCode)
        assertEquals("玉林", ref?.cityName)
        assertEquals("17路", ref?.lineName)
    }

    @Test
    fun stopNameAndSuffixCleaning() {
        assertTrue(MygolbsIds.isMygolbsId("mg:玉林市:市政广场"))
        assertFalse(MygolbsIds.isMygolbsId("cl_s1"))
        assertEquals("市政广场", MygolbsIds.stopNameOf("mg:玉林市:市政广场"))
        assertEquals("一中 [掌上公交]", MygolbsIds.stopNameOf("mg:450900:玉林:一中 [掌上公交]"))
        assertEquals("一中", MygolbsIds.cleanStationName("一中 [掌上公交]"))
        assertEquals("一中", MygolbsIds.cleanStationName("一中（掌上公交）"))
    }
}
