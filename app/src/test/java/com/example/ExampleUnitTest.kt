package com.example

import com.example.data.DataUsageTracker
import com.example.data.PortalRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun parsePortalXmlResponse_success() {
        val xml = """
            <response transactionid="">
                <status>LIVE</status>
                <message><![CDATA[You have successfully logged in]]></message>
            </response>
        """.trimIndent()

        val result = PortalRepository.parsePortalXmlResponse(xml)
        assertTrue(result.isSuccess)
        assertEquals("You have successfully logged in", result.message)
    }

    @Test
    fun parsePortalXmlResponse_alreadySignedIn() {
        val xml = """
            <response transactionid="">
                <status>LOGIN</status>
                <message><![CDATA[You are signed in as student123]]></message>
            </response>
        """.trimIndent()

        val result = PortalRepository.parsePortalXmlResponse(xml)
        assertTrue(result.isSuccess)
        assertEquals("You are signed in as student123", result.message)
    }

    @Test
    fun parsePortalXmlResponse_wrongCredentials() {
        val xml = """
            <response transactionid="">
                <status>FAILED</status>
                <message><![CDATA[Wrong credentials]]></message>
            </response>
        """.trimIndent()

        val result = PortalRepository.parsePortalXmlResponse(xml)
        assertFalse(result.isSuccess)
        assertEquals("Wrong credentials", result.message)
    }

    @Test
    fun parsePortalXmlResponse_dataLimitExceeded() {
        val xml = """
            <response transactionid="">
                <status>FAILED</status>
                <message><![CDATA[Your data limit has been exceeded]]></message>
            </response>
        """.trimIndent()

        val result = PortalRepository.parsePortalXmlResponse(xml)
        assertFalse(result.isSuccess)
        assertEquals("Your data limit has been exceeded", result.message)
    }

    @Test
    fun parsePortalXmlResponse_maxLimitReached() {
        val xml = """
            <response transactionid="">
                <status>FAILED</status>
                <message><![CDATA[Maximum Login Limit Reached]]></message>
            </response>
        """.trimIndent()

        val result = PortalRepository.parsePortalXmlResponse(xml)
        assertFalse(result.isSuccess)
        assertEquals("Maximum Login Limit Reached", result.message)
    }

    @Test
    fun parsePortalXmlResponse_invalidHtmlProxyError() {
        val html = "<html><body>502 Bad Gateway</body></html>"
        val result = PortalRepository.parsePortalXmlResponse(html)
        assertFalse(result.isSuccess)
    }

    @Test
    fun formatBytes_verification() {
        assertEquals("500 B", DataUsageTracker.formatBytes(500L))
        assertEquals("1.5 MB", DataUsageTracker.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("13.00 GB", DataUsageTracker.formatBytes((13L * 1024 * 1024 * 1024)))
    }
}
