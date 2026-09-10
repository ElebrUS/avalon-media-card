package org.ensodai.avalonmediacard.contract.i18n

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Same FQCN as the server copy. Plugin classloaders are parent-first, so at
 * runtime this class is the server's and [Key] identity matches the element
 * installed on the RPC coroutine context.
 */
class PluginRequestOriginElement(val origin: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<PluginRequestOriginElement>
    override val key: CoroutineContext.Key<*> = Key
}

suspend fun currentPluginRequestOrigin(): String? {
    return coroutineContext[PluginRequestOriginElement]
        ?.origin
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
}
