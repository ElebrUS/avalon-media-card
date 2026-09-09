package org.ensodai.avalonmediacard.contract.i18n

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginRequestOriginElementTest {
    @Test
    fun readsOriginFromCoroutineContext() = runBlocking {
        val origin = withContext(PluginRequestOriginElement("http://192.168.0.5:8080/")) {
            currentPluginRequestOrigin()
        }
        assertEquals("http://192.168.0.5:8080", origin)
    }

    @Test
    fun missingOriginIsNull() = runBlocking {
        assertNull(currentPluginRequestOrigin())
    }
}
