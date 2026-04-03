package com.example.familyone.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiServerConfigTest {

    @Test
    fun normalize_addsApiForDomainWithoutPath() {
        val actual = ApiServerConfig.normalizeBaseUrl("https://totalcode.online")
        assertEquals("https://totalcode.online/api", actual)
    }

    @Test
    fun normalize_keepsApiWhenAlreadyPresent() {
        val actual = ApiServerConfig.normalizeBaseUrl("https://totalcode.online/api")
        assertEquals("https://totalcode.online/api", actual)
    }

    @Test
    fun normalize_addsApiForLocalHostWithPort() {
        val actual = ApiServerConfig.normalizeBaseUrl("http://192.168.1.178:5000")
        assertEquals("http://192.168.1.178:5000/api", actual)
    }

    @Test
    fun normalize_forcesHttpsForExternalHttp() {
        val actual = ApiServerConfig.normalizeBaseUrl("http://example.com")
        assertEquals("https://example.com/api", actual)
    }

    @Test
    fun candidateBaseUrls_returnsPrimaryAndLegacy() {
        val actual = ApiServerConfig.candidateBaseUrls("https://x.y/api")
        assertEquals(listOf("https://x.y/api", "https://x.y"), actual)
    }
}
