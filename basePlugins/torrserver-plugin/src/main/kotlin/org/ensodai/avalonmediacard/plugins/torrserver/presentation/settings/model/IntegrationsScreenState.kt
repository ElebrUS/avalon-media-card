package org.ensodai.avalonmediacard.plugins.torrserver.presentation.settings.model

data class TorrServerState(
    val use: Boolean,
    val host: String?,
    val login: String?,
    val pass: String?,
    val useGst: Boolean,
    /** Personal override; empty means use [globalTimelineBufferPercent]. */
    val timelineBufferPercent: String,
    val globalTimelineBufferPercent: String
)
data class SearchEngineState(val use: Boolean, val url: String?, val key: String?)
