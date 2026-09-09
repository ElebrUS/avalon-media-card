package org.ensodai.avalonmediacard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.ensodai.avalonmediacard.di.WebKoinAppConfig
import org.ensodai.avalonmediacard.presentation.App
import org.ensodai.avalonmediacard.presentation.screens.commonComponents.DeviceTarget
import org.ensodai.avalonmediacard.presentation.screens.commonComponents.LocalDeviceTarget
import org.koin.plugin.module.dsl.startKoin
import org.w3c.dom.HTMLElement

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin<WebKoinAppConfig> {}

    val composeRoot = document.getElementById("compose-root") as? HTMLElement
        ?: document.body
        ?: error("Missing #compose-root or body container")

    val deviceTarget = detectWebDeviceTarget()

    ComposeViewport(
        viewportContainer = composeRoot
    ) {
        LaunchedEffect(Unit) {
            val loader = document.getElementById("avalon-html-loader") as? HTMLElement
            if (loader != null) {
                loader.style.opacity = "0"
                delay(300) // Ждем завершения transition: opacity 0.3s
                loader.remove()
            }
        }
        CompositionLocalProvider(LocalDeviceTarget provides deviceTarget) {
            App()
        }
    }
}

internal fun detectWebDeviceTarget(): DeviceTarget {
    val ua = window.navigator.userAgent.lowercase()
    val search = window.location.search.lowercase()
    val isTv = search.contains("ui=tv") ||
        ua.contains("tizen") ||
        ua.contains("smart-tv") ||
        ua.contains("smarttv") ||
        ua.contains("webos") ||
        ua.contains("vidaa") ||
        ua.contains("hbbtv") ||
        (ua.contains("samsung") && ua.contains("tv"))
    return if (isTv) DeviceTarget.TV_WEB else DeviceTarget.DESKTOP_WEB
}