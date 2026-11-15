package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Svi podaci u testovima su izmišljeni. */
class XtreamModelsTest {

    @Test
    fun `account with numeric auth and string expiry`() {
        val json = """
            {"user_info":{"username":"proba","password":"tajna","auth":1,"status":"Active",
             "exp_date":"1893456000","is_trial":"0","max_connections":"1"},
             "server_info":{"url":"tv.invalid","port":"80","server_protocol":"http"}}
        """.trimIndent()
        val info = XtreamJson.decodeFromString(XtreamAccountResponse.serializer(), json).userInfo!!
        assertTrue(info.isAuthenticated)
        assertEquals("Active", info.status)
        assertEquals(1893456000L, info.expiresAtSeconds)
    }

    @Test
    fun `account with null expiry never expires`() {
        val json = """{"user_info":{"auth":"1","status":"Active","exp_date":null}}"""
        val info = XtreamJson.decodeFromString(XtreamAccountResponse.serializer(), json).userInfo!!
        assertTrue(info.isAuthenticated)
        assertNull(info.expiresAtSeconds)
    }

    @Test
    fun `failed login with auth zero`() {
        val json = """{"user_info":{"auth":0}}"""
        val info = XtreamJson.decodeFromString(XtreamAccountResponse.serializer(), json).userInfo!!
        assertFalse(info.isAuthenticated)
    }

    @Test
    fun `failed login with user_info as empty array or missing`() {
        assertNull(XtreamJson.decodeFromString(XtreamAccountResponse.serializer(), """{"user_info":[]}""").userInfo)
        assertNull(XtreamJson.decodeFromString(XtreamAccountResponse.serializer(), """{}""").userInfo)
    }

    @Test
    fun `categories accept string and numeric ids`() {
        val json = """
            [{"category_id":"12","category_name":"Vesti","parent_id":0},
             {"category_id":13,"category_name":"Sport","parent_id":0}]
        """.trimIndent()
        val categories = XtreamJson.decodeFromString(ListSerializer(XtreamCategory.serializer()), json)
        assertEquals(listOf("12", "13"), categories.map { it.id })
        assertEquals(listOf("Vesti", "Sport"), categories.map { it.name })
    }

    @Test
    fun `live streams tolerate mixed types nulls and unknown fields`() {
        val json = """
            [{"num":1,"name":"Prvi Test","stream_type":"live","stream_id":101,
              "stream_icon":"http://img.invalid/1.png","epg_channel_id":"prvi.test","added":"1700000000",
              "category_id":"12","custom_sid":"","tv_archive":1,"direct_source":"","tv_archive_duration":"3"},
             {"num":"2","name":"Drugi Test","stream_id":"102","stream_icon":null,
              "epg_channel_id":null,"category_id":null,"tv_archive":0,"tv_archive_duration":0}]
        """.trimIndent()
        val streams = XtreamJson.decodeFromString(ListSerializer(XtreamLiveStream.serializer()), json)
        assertEquals(2, streams.size)
        assertEquals("101", streams[0].streamId)
        assertEquals("12", streams[0].categoryId)
        assertEquals("1", streams[0].tvArchive)
        assertEquals("3", streams[0].tvArchiveDuration)
        assertEquals("102", streams[1].streamId)
        assertEquals("2", streams[1].num)
        assertNull(streams[1].icon)
        assertNull(streams[1].categoryId)
    }
}
