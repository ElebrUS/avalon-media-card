package org.ensodai.avalonmediacard.plugins.samsungtv

import java.io.File

object SamsungTvPaths {
    const val PLUGIN_ID = "samsung-tv-plugin"
    const val PUBLIC_DIR = "widgets"
    const val PUBLIC_PATH = "/$PUBLIC_DIR"
    const val WGT_FILENAME = "samsung.wgt"
    const val PREVIEW_FILENAME = "preview.json"
    const val CONFIG_FILENAME = "config.xml"
    const val INDEX_FILENAME = "index.html"

    const val TIZEN_PACKAGE_ID = "AvalonMedC"
    const val TIZEN_APP_NAME = "AvalonTV"
    const val TIZEN_APPLICATION_ID = "$TIZEN_PACKAGE_ID.$TIZEN_APP_NAME"

    const val SETTING_ENABLED = "widget_enabled"
    const val SETTING_PUBLIC_URL = "public_base_url"
    const val SETTING_PREVIEW_USER = "preview_user_id"

    /**
     * Writes widget files into the static root the server already serves (`WEB_DIR`).
     * No Samsung-specific HTTP routes are required — drop the JAR into `plugins/`.
     */
    fun widgetOutputDir(
        pluginDir: String,
        webDirEnv: String? = System.getenv("WEB_DIR"),
        workingDir: File = File(".").absoluteFile
    ): File {
        val candidates = buildList {
            webDirEnv?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            add(File(workingDir, "web/build/dist/wasmJs/productionExecutable"))
            add(File(workingDir, "web/build/dist/wasmJs/developmentExecutable"))
            add(File(workingDir, "web"))
        }
        val existingRoot = candidates.firstOrNull { it.isDirectory }
        if (existingRoot != null) {
            return File(existingRoot, PUBLIC_DIR)
        }
        val envRoot = webDirEnv?.takeIf { it.isNotBlank() }?.let { File(it) }
        if (envRoot != null) {
            envRoot.mkdirs()
            if (envRoot.isDirectory) {
                return File(envRoot, PUBLIC_DIR)
            }
        }
        return File(pluginDir, PUBLIC_DIR)
    }

    fun normalizeBaseUrl(raw: String?): String {
        val trimmed = raw?.trim()?.trimEnd('/') ?: ""
        return trimmed
    }

    /**
     * Configured LAN/public URL wins; otherwise the origin of the current RPC request.
     */
    fun effectivePublicBaseUrl(stored: String?, requestOrigin: String? = null): String {
        val configured = normalizeBaseUrl(stored)
        if (configured.isNotBlank()) return configured
        return normalizeBaseUrl(requestOrigin)
    }

    fun publicResourceUrl(baseUrl: String, relativePath: String): String {
        val base = normalizeBaseUrl(baseUrl)
        val path = if (relativePath.startsWith("/")) relativePath else "/$relativePath"
        return if (base.isNotBlank()) "$base$path" else path
    }
}
