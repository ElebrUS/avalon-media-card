package org.ensodai.avalonmediacard.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestOriginResolverTest {
    @Test
    fun prefersOriginHeaderOverHost() {
        val origin = RequestOriginResolver.resolve(
            originHeader = "http://192.168.1.5:8080/",
            forwardedHost = "proxy.example",
            hostHeader = "127.0.0.1:8080"
        )
        assertEquals("http://192.168.1.5:8080", origin)
    }

    @Test
    fun ignoresNonHttpOriginAndUsesHost() {
        val origin = RequestOriginResolver.resolve(
            originHeader = "file://",
            hostHeader = "10.0.0.8:8080",
            fallbackScheme = "http"
        )
        assertEquals("http://10.0.0.8:8080", origin)
    }

    @Test
    fun usesForwardedProtoAndHost() {
        val origin = RequestOriginResolver.resolve(
            originHeader = null,
            forwardedProto = "https, http",
            forwardedHost = "tv.example.com, localhost",
            hostHeader = "127.0.0.1:8080"
        )
        assertEquals("https://tv.example.com", origin)
    }

    @Test
    fun omitsDefaultHttpsPort() {
        val origin = RequestOriginResolver.resolve(
            originHeader = "https://media.local:443/app"
        )
        assertEquals("https://media.local", origin)
    }

    @Test
    fun keepsNonDefaultPortFromHostHeader() {
        val origin = RequestOriginResolver.resolve(
            originHeader = null,
            hostHeader = "192.168.0.10:9090",
            fallbackScheme = "http"
        )
        assertEquals("http://192.168.0.10:9090", origin)
    }

    @Test
    fun returnsNullWhenNothingUsable() {
        assertNull(RequestOriginResolver.resolve(originHeader = "null"))
        assertNull(RequestOriginResolver.resolve(originHeader = " ", hostHeader = " "))
    }
}
