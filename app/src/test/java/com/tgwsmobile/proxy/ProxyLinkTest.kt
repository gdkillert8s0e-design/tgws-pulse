package com.tgwsmobile.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLinkTest {

    @Test
    fun generatedSecretIsThirtyTwoLowercaseHex() {
        repeat(100) {
            val secret = ProxyLink.newSecret()
            assertEquals(32, secret.length)
            assertEquals(secret, secret.lowercase())
            assertTrue(ProxyLink.isValidSecret(secret))
        }
    }

    @Test
    fun generatedSecretsDiffer() {
        assertNotEquals(ProxyLink.newSecret(), ProxyLink.newSecret())
    }

    @Test
    fun invalidSecretsAreRejected() {
        assertFalse(ProxyLink.isValidSecret(""))
        assertFalse(ProxyLink.isValidSecret("abc"))
        assertFalse(ProxyLink.isValidSecret("g".repeat(32)))
        assertFalse(ProxyLink.isValidSecret("0".repeat(31)))
    }

    @Test
    fun fullSecretAddsFakeTlsPrefixAndNormalizesCase() {
        assertEquals(
            "dd0123456789abcdef0123456789abcdef",
            ProxyLink.fullSecret("0123456789ABCDEF0123456789abcdef"),
        )
    }

    @Test
    fun wildcardBindsAreReachedThroughLoopback() {
        assertEquals("127.0.0.1", ProxyLink.clientHost("0.0.0.0"))
        assertEquals("::1", ProxyLink.clientHost("[::]"))
        assertEquals("127.0.0.1", ProxyLink.clientHost("127.0.0.1"))
    }

    @Test
    fun uriHasTelegramProxyShape() {
        assertEquals(
            "tg://proxy?server=127.0.0.1&port=1443&secret=dd00112233",
            ProxyLink.uri("0.0.0.0", 1443, "dd00112233"),
        )
    }

    @Test
    fun maskKeepsOnlyTheEdges() {
        val secret = "0123456789abcdef0123456789abcdef"
        assertEquals("0123••••••••cdef", ProxyLink.mask(secret))
        assertEquals("••••••••", ProxyLink.mask("abc"))
        assertFalse(ProxyLink.mask(secret).contains("456789ab"))
    }
}
