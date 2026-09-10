package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import org.ensodai.avalonmediacard.contract.model.EntityType
import org.ensodai.avalonmediacard.contract.model.MediaKey
import org.ensodai.avalonmediacard.contract.model.MediaProvider
import org.ensodai.avalonmediacard.contract.model.MediaStatus
import org.ensodai.avalonmediacard.contract.model.MediaType
import org.ensodai.avalonmediacard.contract.i18n.currentPluginRequestOrigin
import org.ensodai.avalonmediacard.contract.plugins.PluginContext
import org.ensodai.avalonmediacard.plugins.samsungtv.SamsungTvPaths
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.uuid.Uuid

class SamsungWidgetRegistrar(
    private val context: PluginContext,
    private val pluginVersion: String
) {
    fun outputDir(): File = SamsungTvPaths.widgetOutputDir(context.pluginDir)

    fun publishBundledResources(publicBaseUrl: String = "", previewJson: String = emptyPreviewJson()) {
        val dir = outputDir().apply { mkdirs() }
        val indexHtml = loadResource("tizen-widget/index.html")
            .replace("%%AVALON_SERVER_URL%%", publicBaseUrl)
            .replace("%%AVALON_WIDGET_VERSION%%", pluginVersion)
        val launcherJs = loadResource("tizen-widget/launcher.js")
            .replace("%%AVALON_SERVER_URL%%", publicBaseUrl)
        val stylesCss = loadResource("tizen-widget/styles.css")
        val iconBytes = loadResourceBytes("tizen-widget/icon.png")
        val configXml = TizenWidgetManifest.renderConfigXml(publicBaseUrl, pluginVersion)
        val msxStart = TizenWidgetManifest.renderMsxStart(publicBaseUrl)
        val msxMenu = TizenWidgetManifest.renderMsxMenu(publicBaseUrl)

        writeText(File(dir, SamsungTvPaths.INDEX_FILENAME), indexHtml)
        writeText(File(dir, "launcher.js"), launcherJs)
        writeText(File(dir, "styles.css"), stylesCss)
        writeText(File(dir, SamsungTvPaths.CONFIG_FILENAME), configXml)
        writeText(File(dir, SamsungTvPaths.PREVIEW_FILENAME), previewJson)
        File(dir, "icon.png").writeBytes(iconBytes)

        val msxDir = File(dir, "msx").apply { mkdirs() }
        writeText(File(msxDir, "start.json"), msxStart)
        writeText(File(msxDir, "menu.json"), msxMenu)

        val widgetFiles = linkedMapOf(
            "config.xml" to configXml.toByteArray(StandardCharsets.UTF_8),
            "index.html" to indexHtml.toByteArray(StandardCharsets.UTF_8),
            "launcher.js" to launcherJs.toByteArray(StandardCharsets.UTF_8),
            "styles.css" to stylesCss.toByteArray(StandardCharsets.UTF_8),
            "icon.png" to iconBytes,
            "preview.json" to previewJson.toByteArray(StandardCharsets.UTF_8)
        )
        File(dir, SamsungTvPaths.WGT_FILENAME).writeBytes(TizenWidgetPackager.pack(widgetFiles))
    }

    suspend fun register(previewUserId: Uuid? = null) {
        val enabled = context.settings.getBoolean(SamsungTvPaths.SETTING_ENABLED, true)
        val publicBaseUrl = SamsungTvPaths.effectivePublicBaseUrl(
            context.settings.getString(SamsungTvPaths.SETTING_PUBLIC_URL),
            currentPluginRequestOrigin()
        )
        val previewJson = buildPreviewJson(publicBaseUrl, previewUserId)
        publishBundledResources(publicBaseUrl, previewJson)
        context.logger.info(
            "Samsung TV widget registered at ${outputDir().absolutePath} (enabled=$enabled, publicUrl=${publicBaseUrl.ifBlank { "<unset>" }})"
        )
    }

    private fun emptyPreviewJson(): String {
        return SamsungPreviewBuilder.encode(
            SamsungPreviewBuilder.build(
                items = emptyList(),
                publicBaseUrl = "",
                sectionTitle = "Continue Watching"
            )
        )
    }

    private suspend fun buildPreviewJson(publicBaseUrl: String, previewUserId: Uuid?): String {
        val storedUser = context.settings.getString(SamsungTvPaths.SETTING_PREVIEW_USER)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        val userId = previewUserId ?: storedUser
        val items = if (userId != null) {
            loadContinueWatching(userId, publicBaseUrl)
        } else {
            emptyList()
        }
        val sectionTitle = context.i18n.tForLocale("en", "preview.continue_watching")
        return SamsungPreviewBuilder.encode(
            SamsungPreviewBuilder.build(items, publicBaseUrl, sectionTitle)
        )
    }

    private suspend fun loadContinueWatching(userId: Uuid, publicBaseUrl: String): List<SamsungPreviewItem> {
        val movies = runCatching { context.userMovies.getUserMovies(userId) }.getOrDefault(emptyList())
        val watching = movies
            .filter { it.status == MediaStatus.WATCHING || it.progressSeconds > 0L }
            .sortedByDescending { it.lastWatchedAt }
            .take(8)
        if (watching.isEmpty()) return emptyList()

        val keys = watching.map { item ->
            MediaKey(
                provider = MediaProvider.Tmdb,
                type = if (item.mediaType == MediaType.MOVIE) EntityType.MOVIE else EntityType.TV,
                id = item.mediaId
            )
        }
        val details = runCatching {
            context.catalog.getMediaDetailsBatch(
                keys = keys,
                requireSeasons = false,
                requireVideos = false,
                language = "ru"
            )
        }.getOrDefault(emptyMap())

        return keys.mapIndexedNotNull { index, key ->
            val item = watching[index]
            val meta = details[key] ?: return@mapIndexedNotNull null
            val subtitle = if (item.durationSeconds > 0L && item.progressSeconds > 0L) {
                val percent = ((item.progressSeconds * 100L) / item.durationSeconds).toInt().coerceIn(0, 99)
                "$percent%"
            } else {
                null
            }
            val image = meta.backgroundUrl ?: meta.posterUrl
            val openPath = "/?ui=tv"
            SamsungPreviewItem(
                title = meta.title,
                subtitle = subtitle,
                imageUrl = image,
                actionData = SamsungPreviewBuilder.toAbsoluteUrl(openPath, publicBaseUrl) ?: openPath,
                isPlayable = true
            )
        }
    }

    private fun loadResource(path: String): String {
        return loadResourceBytes(path).toString(StandardCharsets.UTF_8)
    }

    private fun loadResourceBytes(path: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Missing plugin resource: $path")
        return stream.use { it.readBytes() }
    }

    private fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }
}
