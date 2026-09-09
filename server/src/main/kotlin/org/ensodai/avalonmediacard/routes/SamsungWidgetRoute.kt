package org.ensodai.avalonmediacard.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.ensodai.avalonmediacard.plugin.PluginManager
import java.io.File

private const val WGT_FILENAME = "avalon-samsung.wgt"

fun Route.samsungWidgetRoutes(pluginManager: PluginManager) {
    val widgetDir = samsungWidgetDir().apply { mkdirs() }

    staticFiles("/samsung-widget", widgetDir, index = "index.html")

    get("/samsung-widget.wgt") {
        val wgt = File(widgetDir, WGT_FILENAME)
        if (!wgt.isFile) {
            val message = if (pluginManager.isPluginLoaded("samsung-tv-plugin")) {
                "Samsung TV widget is not registered yet"
            } else {
                "Samsung TV widget plugin is not loaded"
            }
            return@get call.respondText(message, status = HttpStatusCode.NotFound)
        }
        call.response.header("Content-Disposition", "attachment; filename=\"$WGT_FILENAME\"")
        call.respondBytes(wgt.readBytes(), ContentType.parse("application/widget"))
    }
}

fun samsungWidgetDir(): File {
    val dataDir = System.getenv("DATA_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: File("data")
    return File(dataDir, "samsung-tv-widget")
}
