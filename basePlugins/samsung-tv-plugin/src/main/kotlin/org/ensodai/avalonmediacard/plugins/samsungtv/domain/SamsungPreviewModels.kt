package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SamsungPreviewDocument(
    val expires: String,
    @SerialName("expires_only") val expiresOnly: Boolean = false,
    val sections: List<SamsungPreviewSection> = emptyList()
)

@Serializable
data class SamsungPreviewSection(
    val title: String,
    val position: String = "0",
    val tiles: List<SamsungPreviewTile> = emptyList()
)

@Serializable
data class SamsungPreviewTile(
    val title: String,
    val subtitle: String? = null,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("image_ratio") val imageRatio: String = "16by9",
    @SerialName("action_data") val actionData: String,
    @SerialName("is_playable") val isPlayable: Boolean = true
)

data class SamsungPreviewItem(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String?,
    val actionData: String,
    val isPlayable: Boolean = true
)
