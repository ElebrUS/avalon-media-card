package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import org.ensodai.avalonmediacard.plugins.samsungtv.SamsungTvPaths
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TizenWidgetManifestTest {
    @Test
    fun configXmlRegistersSamsungTvWidgetAndPreview() {
        val xml = TizenWidgetManifest.renderConfigXml("http://192.168.1.10:8080", "1.0.0")

        assertContains(xml, "xmlns:tizen=\"http://tizen.org/ns/widgets\"")
        assertContains(xml, "id=\"${SamsungTvPaths.TIZEN_APPLICATION_ID}\"")
        assertContains(xml, "package=\"${SamsungTvPaths.TIZEN_PACKAGE_ID}\"")
        assertContains(xml, "<tizen:profile name=\"tv-samsung\"/>")
        assertContains(xml, "http://tizen.org/privilege/tv.inputdevice")
        assertContains(
            xml,
            "http://samsung.com/tv/metadata/preview/preview_json"
        )
        assertContains(xml, "http://192.168.1.10:8080/samsung-widget/preview.json")
        assertContains(xml, "version=\"1.0.0\"")
    }

    @Test
    fun msxStartPointsAtGeneratedMenu() {
        val start = TizenWidgetManifest.renderMsxStart("http://10.0.0.5:8080/")
        assertContains(start, "http://10.0.0.5:8080/samsung-widget/msx/menu.json")
    }
}

class SamsungPreviewBuilderTest {
    @Test
    fun buildsSmartHubPreviewTilesWithAbsoluteImageUrls() {
        val document = SamsungPreviewBuilder.build(
            items = listOf(
                SamsungPreviewItem(
                    title = "Dune",
                    subtitle = "45%",
                    imageUrl = "/api/media/image/w1280/dune.jpg",
                    actionData = "/?ui=tv"
                )
            ),
            publicBaseUrl = "http://192.168.1.10:8080",
            sectionTitle = "Continue Watching"
        )

        assertEquals("Continue Watching", document.sections.single().title)
        val tile = document.sections.single().tiles.single()
        assertEquals("Dune", tile.title)
        assertEquals("45%", tile.subtitle)
        assertEquals("http://192.168.1.10:8080/api/media/image/w1280/dune.jpg", tile.imageUrl)
        assertEquals("16by9", tile.imageRatio)
        assertTrue(tile.isPlayable)

        val json = SamsungPreviewBuilder.encode(document)
        assertContains(json, "\"expires\"")
        assertContains(json, "Continue Watching")
        assertContains(json, "dune.jpg")
    }

    @Test
    fun skipsTilesWithoutImages() {
        val document = SamsungPreviewBuilder.build(
            items = listOf(
                SamsungPreviewItem(
                    title = "No Art",
                    imageUrl = null,
                    actionData = "/?ui=tv"
                )
            ),
            publicBaseUrl = "http://host",
            sectionTitle = "Continue Watching"
        )
        assertTrue(document.sections.single().tiles.isEmpty())
    }
}

class TizenWidgetPackagerTest {
    @Test
    fun packsRequiredTizenWidgetFiles() {
        val wgt = TizenWidgetPackager.pack(
            mapOf(
                "config.xml" to "<widget/>".toByteArray(),
                "index.html" to "<html></html>".toByteArray(),
                "icon.png" to byteArrayOf(1, 2, 3)
            )
        )
        assertTrue(wgt.size > 20)

        val names = mutableListOf<String>()
        ZipInputStream(wgt.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }
        assertEquals(listOf("config.xml", "index.html", "icon.png"), names)
    }

    @Test
    fun rejectsWidgetWithoutConfig() {
        assertFailsWith<IllegalArgumentException> {
            TizenWidgetPackager.pack(mapOf("index.html" to "<html></html>".toByteArray()))
        }
    }
}

class SamsungTvPathsTest {
    @Test
    fun normalizeBaseUrlStripsTrailingSlash() {
        assertEquals("http://192.168.1.10:8080", SamsungTvPaths.normalizeBaseUrl(" http://192.168.1.10:8080/ "))
        assertEquals("", SamsungTvPaths.normalizeBaseUrl("  "))
    }

    @Test
    fun effectivePublicBaseUrlFallsBackToRequestOrigin() {
        assertEquals(
            "http://192.168.1.10:8080",
            SamsungTvPaths.effectivePublicBaseUrl("http://192.168.1.10:8080/", "http://browser.local:9090")
        )
        assertEquals(
            "http://browser.local:9090",
            SamsungTvPaths.effectivePublicBaseUrl("  ", "http://browser.local:9090/")
        )
        assertEquals("", SamsungTvPaths.effectivePublicBaseUrl(null, null))
        assertEquals("", SamsungTvPaths.effectivePublicBaseUrl("", ""))
    }

    @Test
    fun publicResourceUrlJoinsBaseAndPath() {
        assertEquals(
            "http://192.168.1.10:8080/samsung-widget/",
            SamsungTvPaths.publicResourceUrl("http://192.168.1.10:8080", "/samsung-widget/")
        )
        assertEquals("/samsung-widget/preview.json", SamsungTvPaths.publicResourceUrl("", "/samsung-widget/preview.json"))
    }

    @Test
    fun widgetOutputDirUsesExistingWebRootWithoutServerRoutes() {
        val tmp = kotlin.io.path.createTempDirectory("samsung-tv-paths").toFile()
        try {
            val web = File(tmp, "web").apply { mkdirs() }
            val plugins = File(tmp, "plugins").apply { mkdirs() }
            val out = SamsungTvPaths.widgetOutputDir(
                pluginDir = plugins.absolutePath,
                webDirEnv = null,
                workingDir = tmp
            )
            assertEquals(File(web, "samsung-widget").canonicalFile, out.canonicalFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun widgetOutputDirPrefersWebDirEnv() {
        val tmp = kotlin.io.path.createTempDirectory("samsung-tv-webdir").toFile()
        try {
            val dist = File(tmp, "dist").apply { mkdirs() }
            File(tmp, "web").mkdirs()
            val plugins = File(tmp, "plugins").apply { mkdirs() }
            val out = SamsungTvPaths.widgetOutputDir(
                pluginDir = plugins.absolutePath,
                webDirEnv = dist.absolutePath,
                workingDir = tmp
            )
            assertEquals(File(dist, "samsung-widget").canonicalFile, out.canonicalFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun widgetOutputDirFallsBackToPluginDir() {
        val tmp = kotlin.io.path.createTempDirectory("samsung-tv-fallback").toFile()
        try {
            val cwd = File(tmp, "cwd").apply { mkdirs() }
            val plugins = File(tmp, "plugins").apply { mkdirs() }
            val out = SamsungTvPaths.widgetOutputDir(
                pluginDir = plugins.absolutePath,
                webDirEnv = null,
                workingDir = cwd
            )
            assertEquals(File(plugins, "samsung-widget").canonicalFile, out.canonicalFile)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
