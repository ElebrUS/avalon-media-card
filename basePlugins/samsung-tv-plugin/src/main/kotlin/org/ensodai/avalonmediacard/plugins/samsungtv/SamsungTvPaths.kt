package org.ensodai.avalonmediacard.plugins.samsungtv

import java.io.File

object SamsungTvPaths {
    const val PLUGIN_ID = "samsung-tv-plugin"
    const val PUBLIC_PATH = "/samsung-widget"
    const val WGT_FILENAME = "avalon-samsung.wgt"
    const val PREVIEW_FILENAME = "preview.json"
    const val CONFIG_FILENAME = "config.xml"
    const val INDEX_FILENAME = "index.html"

    const val TIZEN_PACKAGE_ID = "AvalonMedC"
    const val TIZEN_APP_NAME = "AvalonTV"
    const val TIZEN_APPLICATION_ID = "$TIZEN_PACKAGE_ID.$TIZEN_APP_NAME"

    const val SETTING_ENABLED = "widget_enabled"
    const val SETTING_PUBLIC_URL = "public_base_url"
    const val SETTING_PREVIEW_USER = "preview_user_id"

    fun widgetOutputDir(): File {
        val dataDir = System.getenv("DATA_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: File("data")
        return File(dataDir, "samsung-tv-widget")
    }

    fun normalizeBaseUrl(raw: String?): String {
        val trimmed = raw?.trim()?.trimEnd('/') ?: ""
        return trimmed
    }
}
