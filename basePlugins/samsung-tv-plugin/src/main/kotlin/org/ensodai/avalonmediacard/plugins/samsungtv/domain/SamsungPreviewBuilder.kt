package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SamsungPreviewBuilder {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun build(
        items: List<SamsungPreviewItem>,
        publicBaseUrl: String,
        sectionTitle: String,
        expiresHours: Long = 24
    ): SamsungPreviewDocument {
        val expires = OffsetDateTime.now(ZoneOffset.UTC)
            .plusHours(expiresHours)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val tiles = items.mapNotNull { item ->
            val imageUrl = toAbsoluteUrl(item.imageUrl, publicBaseUrl) ?: return@mapNotNull null
            SamsungPreviewTile(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = imageUrl,
                actionData = item.actionData,
                isPlayable = item.isPlayable
            )
        }
        return SamsungPreviewDocument(
            expires = expires,
            expiresOnly = false,
            sections = listOf(
                SamsungPreviewSection(
                    title = sectionTitle,
                    position = "0",
                    tiles = tiles
                )
            )
        )
    }

    fun encode(document: SamsungPreviewDocument): String = json.encodeToString(document)

    fun toAbsoluteUrl(url: String?, publicBaseUrl: String): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val base = publicBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return raw
        return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
    }
}
