package org.ensodai.avalonmediacard.presentation.screens.episodesNotificationsScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.ensodai.avalonmediacard.presentation.core.SduiCoordinator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EpisodesNotificationsScreen(
    expectedItemsCount: Int? = null,
    viewModel: EpisodesNotificationsViewModel = koinViewModel()
) {
    val state by viewModel.viewState.collectAsState()

    SduiCoordinator(viewModel) { dispatch ->
        EpisodesNotificationsContent(
            state = state,
            onAction = dispatch,
            onFilterSelected = viewModel::onFilterSelected,
            expectedItemsCount = expectedItemsCount
        )
    }
}
