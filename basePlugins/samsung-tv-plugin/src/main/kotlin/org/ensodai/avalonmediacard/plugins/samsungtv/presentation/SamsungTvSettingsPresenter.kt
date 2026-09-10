package org.ensodai.avalonmediacard.plugins.samsungtv.presentation

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.ensodai.avalonmediacard.contract.plugins.PluginContext
import org.ensodai.avalonmediacard.contract.plugins.ScreenSlots
import org.ensodai.avalonmediacard.contract.slot.ActionOpenUrl
import org.ensodai.avalonmediacard.contract.slot.LayoutNode
import org.ensodai.avalonmediacard.contract.slot.ScreenStreamEvent
import org.ensodai.avalonmediacard.contract.slot.SettingField
import org.ensodai.avalonmediacard.contract.slot.SlotData
import org.ensodai.avalonmediacard.contract.slot.SlotId
import org.ensodai.avalonmediacard.contract.slot.SlotState
import org.ensodai.avalonmediacard.contract.slot.SlotUpdate
import org.ensodai.avalonmediacard.contract.slot.ValidationStatus
import org.ensodai.avalonmediacard.contract.i18n.currentPluginRequestOrigin
import org.ensodai.avalonmediacard.plugins.samsungtv.SamsungTvPaths
import org.ensodai.avalonmediacard.plugins.samsungtv.domain.SaveSamsungTvSettingsCommand
import kotlin.uuid.Uuid

class SamsungTvSettingsPresenter(
    private val pluginId: String,
    private val context: PluginContext
) {
    val layoutNodes = listOf(
        LayoutNode("${pluginId}_widget", SlotId.Integrations)
    )

    fun getIntegrationsSlots(userId: Uuid?): ScreenSlots {
        if (userId == null) {
            return ScreenSlots(layout = emptyList(), flow = kotlinx.coroutines.flow.emptyFlow())
        }

        val enabledFlow = context.settings.observeBoolean(SamsungTvPaths.SETTING_ENABLED, true)
        val publicUrlFlow = context.settings.observeString(SamsungTvPaths.SETTING_PUBLIC_URL, "")

        val flow = combine(enabledFlow, publicUrlFlow) { enabled, publicUrl ->
            buildSlot(enabled, publicUrl.orEmpty())
        }.map { ScreenStreamEvent.Update(it) }

        return ScreenSlots(layout = layoutNodes, flow = flow)
    }

    private suspend fun buildSlot(enabled: Boolean, publicUrl: String): SlotUpdate {
        val effectiveBase = SamsungTvPaths.effectivePublicBaseUrl(publicUrl, currentPluginRequestOrigin())
        val widgetUrl = SamsungTvPaths.publicResourceUrl(effectiveBase, "${SamsungTvPaths.PUBLIC_PATH}/")
        val wgtUrl = SamsungTvPaths.publicResourceUrl(
            effectiveBase,
            "${SamsungTvPaths.PUBLIC_PATH}/${SamsungTvPaths.WGT_FILENAME}"
        )
        val msxUrl = SamsungTvPaths.publicResourceUrl(effectiveBase, "${SamsungTvPaths.PUBLIC_PATH}/msx/start.json")
        val previewUrl = SamsungTvPaths.publicResourceUrl(
            effectiveBase,
            "${SamsungTvPaths.PUBLIC_PATH}/${SamsungTvPaths.PREVIEW_FILENAME}"
        )

        return SlotUpdate(
            slotId = SlotId.Integrations,
            nodeId = "${pluginId}_widget",
            state = SlotState.Content(
                SlotData.SettingsGroup(
                    title = context.i18n.t("settings.title"),
                    description = context.i18n.t("settings.description"),
                    fields = listOf(
                        SettingField.Toggle(
                            key = SamsungTvPaths.SETTING_ENABLED,
                            label = context.i18n.t("settings.enabled"),
                            value = enabled,
                            onChangeAction = SaveSamsungTvSettingsCommand()
                        ),
                        SettingField.TextField(
                            key = SamsungTvPaths.SETTING_PUBLIC_URL,
                            label = context.i18n.t("settings.public_url"),
                            value = publicUrl,
                            placeholder = "http://192.168.1.10:8080",
                            isSensitive = false,
                            isEnabled = enabled
                        ),
                        SettingField.Info(
                            key = "widget_url",
                            label = context.i18n.t("settings.widget_url"),
                            description = widgetUrl,
                            action = ActionOpenUrl(widgetUrl),
                            actionLabel = context.i18n.t("settings.open_widget")
                        ),
                        SettingField.Info(
                            key = "wgt_download",
                            label = context.i18n.t("settings.wgt_file"),
                            description = wgtUrl,
                            action = ActionOpenUrl(wgtUrl),
                            actionLabel = context.i18n.t("settings.download_wgt")
                        ),
                        SettingField.Info(
                            key = "preview_url",
                            label = context.i18n.t("settings.preview_url"),
                            description = previewUrl
                        ),
                        SettingField.Info(
                            key = "msx_url",
                            label = context.i18n.t("settings.msx_url"),
                            description = msxUrl
                        ),
                        SettingField.Info(
                            key = "install_hint",
                            label = context.i18n.t("settings.install_title"),
                            description = context.i18n.t("settings.install_steps")
                        )
                    ),
                    saveAction = SaveSamsungTvSettingsCommand(),
                    saveActionLabel = context.i18n.t("settings.save"),
                    isSaveEnabled = enabled,
                    connectionStatus = if (enabled && effectiveBase.isNotBlank()) {
                        ValidationStatus.Success
                    } else {
                        ValidationStatus.None
                    }
                )
            )
        )
    }
}
