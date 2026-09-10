package org.ensodai.avalonmediacard.security

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.host
import java.net.URI

/**
 * Resolves the client-facing origin of an incoming request so plugins can
 * build absolute LAN URLs without a dedicated setting.
 */
object RequestOriginResolver {
    fun fromCall(call: ApplicationCall): String? {
        val headerOrigin = normalizeHttpOrigin(call.request.header(HttpHeaders.Origin))
        if (headerOrigin != null) return headerOrigin

        val forwardedProto = firstHeaderValue(call.request.header("X-Forwarded-Proto"))
        val forwardedHost = firstHeaderValue(call.request.header("X-Forwarded-Host"))
        val hostHeader = firstHeaderValue(call.request.header(HttpHeaders.Host))
            ?: call.request.host().takeIf { it.isNotBlank() }
        val point = call.request.origin
        return resolve(
            originHeader = null,
            forwardedProto = forwardedProto ?: point.scheme,
            forwardedHost = forwardedHost,
            hostHeader = hostHeader ?: point.serverHost,
            fallbackScheme = point.scheme.ifBlank { "http" },
            fallbackPort = point.serverPort.takeIf { it > 0 }
        )
    }

    fun resolve(
        originHeader: String?,
        forwardedProto: String? = null,
        forwardedHost: String? = null,
        hostHeader: String? = null,
        fallbackScheme: String = "http",
        fallbackPort: Int? = null
    ): String? {
        normalizeHttpOrigin(originHeader)?.let { return it }

        val hostRaw = firstHeaderValue(forwardedHost) ?: firstHeaderValue(hostHeader) ?: return null
        val scheme = normalizeScheme(firstHeaderValue(forwardedProto)) ?: normalizeScheme(fallbackScheme) ?: "http"
        val (host, portFromHost) = splitHostPort(hostRaw)
        if (host.isBlank() || host.equals("unknown", ignoreCase = true)) return null
        return formatOrigin(scheme, host, portFromHost ?: fallbackPort)
    }

    fun normalizeHttpOrigin(raw: String?): String? {
        val value = raw?.trim()?.trimEnd('/') ?: return null
        if (value.isBlank() || value.equals("null", ignoreCase = true) || value.equals("undefined", ignoreCase = true)) {
            return null
        }
        return try {
            val uri = URI(value)
            val scheme = normalizeScheme(uri.scheme) ?: return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            formatOrigin(scheme, host, uri.port.takeIf { it > 0 })
        } catch (_: Exception) {
            null
        }
    }

    internal fun formatOrigin(scheme: String, host: String, port: Int?): String {
        val omitPort = port == null || port <= 0 ||
            (scheme == "http" && port == 80) ||
            (scheme == "https" && port == 443)
        return if (omitPort) "$scheme://$host" else "$scheme://$host:$port"
    }

    internal fun splitHostPort(hostPort: String): Pair<String, Int?> {
        val value = hostPort.trim()
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end > 0) {
                val host = value.substring(1, end)
                val port = value.substring(end + 1).removePrefix(":").toIntOrNull()
                return host to port
            }
        }
        val colon = value.lastIndexOf(':')
        if (colon > 0 && value.indexOf(':') == colon) {
            val port = value.substring(colon + 1).toIntOrNull()
            if (port != null) return value.substring(0, colon) to port
        }
        return value to null
    }

    private fun normalizeScheme(raw: String?): String? {
        val scheme = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return if (scheme == "http" || scheme == "https") scheme else null
    }

    private fun firstHeaderValue(raw: String?): String? {
        return raw?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}
