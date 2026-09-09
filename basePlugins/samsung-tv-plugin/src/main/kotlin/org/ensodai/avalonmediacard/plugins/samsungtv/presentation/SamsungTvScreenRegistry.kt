package org.ensodai.avalonmediacard.plugins.samsungtv.presentation

import org.ensodai.avalonmediacard.contract.plugins.PluginContext
import org.ensodai.avalonmediacard.contract.slot.SlotId
import org.ensodai.avalonmediacard.contract.ui.navigation.Screen

object SamsungTvScreenRegistry {
    fun register(
        context: PluginContext,
        pluginId: String,
        settingsPresenter: SamsungTvSettingsPresenter
    ) {
        context.slots.declare<Screen.Integrations>(
            listOf(SlotId.Integrations)
        ) {
            settingsPresenter.layoutNodes
        }
        context.slots.onScreen<Screen.Integrations> { _, userId ->
            settingsPresenter.getIntegrationsSlots(userId)
        }
    }
}
