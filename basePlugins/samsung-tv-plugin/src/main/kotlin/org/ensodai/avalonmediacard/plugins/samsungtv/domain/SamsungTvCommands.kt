package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.ensodai.avalonmediacard.contract.slot.ServerAction
import org.ensodai.avalonmediacard.contract.slot.TemplateAction

@Serializable
data class SaveSamsungTvSettingsCommand(
    @SerialName("widget_enabled") val widgetEnabled: String? = null,
    @SerialName("public_base_url") val publicBaseUrl: String? = null
) : TemplateAction {
    override fun withParameter(key: String, value: Any): TemplateAction {
        return when (key) {
            "widget_enabled" -> copy(widgetEnabled = value.toString())
            "public_base_url" -> copy(publicBaseUrl = value.toString())
            else -> this
        }
    }
}

@Serializable
data object RegisterSamsungWidgetCommand : ServerAction
