package org.ensodai.avalonmediacard.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ensodai.avalonmediacard.contract.auth.AuthState
import org.ensodai.avalonmediacard.contract.i18n.PluginLocaleElement
import org.ensodai.avalonmediacard.contract.i18n.PluginRequestOriginElement
import org.ensodai.avalonmediacard.contract.i18n.PluginUserElement
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

class RpcSessionContext {
    private val _state = MutableStateFlow<AuthState>(AuthState.Guest)
    val state = _state.asStateFlow()

    @Volatile
    var requestOrigin: String? = null

    fun updateState(newState: AuthState) {
        _state.value = newState
    }

    suspend fun awaitUserId(): Uuid {
        return state.filterIsInstance<AuthState.Authorized>().first().userId
    }

    fun pluginCoroutineContext(locale: String, userId: Uuid?): CoroutineContext {
        var ctx: CoroutineContext = PluginLocaleElement(locale)
        if (userId != null) {
            ctx += PluginUserElement(userId)
        }
        val origin = requestOrigin
        if (!origin.isNullOrBlank()) {
            ctx += PluginRequestOriginElement(origin)
        }
        return ctx
    }
}
