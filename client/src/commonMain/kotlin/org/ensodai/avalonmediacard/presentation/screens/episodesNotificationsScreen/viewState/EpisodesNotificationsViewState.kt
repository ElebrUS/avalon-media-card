package org.ensodai.avalonmediacard.presentation.screens.episodesNotificationsScreen.viewState

import org.ensodai.avalonmediacard.contract.slot.ServerAction
import org.ensodai.avalonmediacard.contract.slot.SlotData
import org.ensodai.avalonmediacard.presentation.core.SduiSlot
import org.ensodai.avalonmediacard.presentation.core.SduiViewState

enum class EpisodeFilter {
    ALL,
    RECENT_7_DAYS,
    UNWATCHED
}

data class EpisodesNotificationsViewState(
    val feedSlot: SduiSlot<SlotData.EpisodesFeed>? = null,
    val selectedFilter: EpisodeFilter = EpisodeFilter.ALL,
    override val loadingActions: Set<ServerAction> = emptySet()
) : SduiViewState
