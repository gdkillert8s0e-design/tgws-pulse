package com.tgwsmobile.proxy

import java.security.SecureRandom

/**
 * Pure helpers with no Android types, so they are covered by plain JVM tests.
 * The secret is 16 random bytes in lowercase hex; Telegram receives it with the
 * fake-TLS "dd" prefix.
 */
object ProxyLink {
    private val HEX32 = Regex("[0-9a-f]{32}")
    private val random = SecureRandom()

    /** A fresh secret from the platform CSPRNG. */
    fun newSecret(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidSecret(value: String): Boolean = HEX32.matches(value.trim().lowercase())

    /** The secret as Telegram expects it in a proxy link. */
    fun fullSecret(secret: String): String = "dd" + secret.trim().lowercase()

    /** Host Telegram should dial. Wildcard binds are reached through loopback. */
    fun clientHost(host: String): String = when (host.trim()) {
        "0.0.0.0", "" -> "127.0.0.1"
        "::", "[::]" -> "::1"
        else -> host.trim()
    }

    /**
     * The tg://proxy deep link. Every component is either an IP literal, a
     * decimal number or hex, so no percent-encoding is needed.
     */
    fun uri(host: String, port: Int, fullSecret: String): String =
        "tg://proxy?server=${clientHost(host)}&port=$port&secret=$fullSecret"

    /** Shows only the edges of a secret, for the UI and for logs. */
    fun mask(secret: String): String =
        if (secret.length <= 8) "••••••••" else secret.take(4) + "••••••••" + secret.takeLast(4)
}
