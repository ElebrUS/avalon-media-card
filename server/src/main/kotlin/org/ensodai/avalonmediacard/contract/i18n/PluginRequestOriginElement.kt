package org.ensodai.avalonmediacard.contract.i18n

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Public origin of the current RPC session (`https://host:port`), taken from
 * the WebSocket upgrade (`Origin` / forwarded Host). Plugins can fall back to
 * this when a LAN/public URL setting is empty.
 *
 * Lives in this module (not the published contract) so drop-in plugins can
 * still compile against contract 1.0.3. The Samsung TV plugin ships the same
 * FQCN; the plugin classloader is parent-first, so at runtime both sides share
 * this class and [Key] identity matches.
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
