package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import org.ensodai.avalonmediacard.plugins.samsungtv.SamsungTvPaths

object TizenWidgetManifest {
    fun renderConfigXml(
        publicBaseUrl: String,
        version: String
    ): String {
        val base = publicBaseUrl.trim().trimEnd('/')
        val previewUrl = if (base.isNotBlank()) {
            "$base${SamsungTvPaths.PUBLIC_PATH}/${SamsungTvPaths.PREVIEW_FILENAME}"
        } else {
            SamsungTvPaths.PREVIEW_FILENAME
        }
        val widgetId = if (base.isNotBlank()) base else "http://ensodai.org/avalonmediacard"
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <widget xmlns="http://www.w3.org/ns/widgets" xmlns:tizen="http://tizen.org/ns/widgets" id="$widgetId" version="$version" viewmodes="maximized">
                <access origin="*" subdomains="true"></access>
                <tizen:application id="${SamsungTvPaths.TIZEN_APPLICATION_ID}" package="${SamsungTvPaths.TIZEN_PACKAGE_ID}" required_version="2.3"/>
                <content src="index.html"/>
                <feature name="http://tizen.org/feature/screen.size.normal.1080.1920"/>
                <icon src="icon.png"/>
                <name>Avalon MediaCard</name>
                <tizen:metadata key="http://tizen.org/metadata/app_ui_type/base_screen_resolution" value="extensive"/>
                <tizen:metadata key="http://samsung.com/tv/metadata/preview/preview_json" value="$previewUrl"/>
                <tizen:privilege name="http://tizen.org/privilege/internet"/>
                <tizen:privilege name="http://tizen.org/privilege/tv.inputdevice"/>
                <tizen:privilege name="http://tizen.org/privilege/tv.audio"/>
                <tizen:privilege name="http://tizen.org/privilege/volume.set"/>
                <tizen:profile name="tv-samsung"/>
                <tizen:setting screen-orientation="landscape" context-menu="disable" background-support="disable" encryption="disable" install-location="auto" hwkey-event="enable"/>
            </widget>
        """.trimIndent() + "\n"
    }

    fun renderMsxStart(publicBaseUrl: String): String {
        val base = publicBaseUrl.trim().trimEnd('/')
        val menuUrl = if (base.isNotBlank()) {
            "$base${SamsungTvPaths.PUBLIC_PATH}/msx/menu.json"
        } else {
            "menu.json"
        }
        return """
            {
              "name": "Avalon MediaCard",
              "version": "1.0.0",
              "parameter": "menu:$menuUrl"
            }
        """.trimIndent() + "\n"
    }

    fun renderMsxMenu(publicBaseUrl: String): String {
        val base = publicBaseUrl.trim().trimEnd('/')
        val openUrl = if (base.isNotBlank()) "$base/?ui=tv" else "/?ui=tv"
        return """
            {
              "headline": "Avalon MediaCard",
              "menu": [
                {
                  "type": "default",
                  "label": "Open Avalon",
                  "action": "link:$openUrl"
                }
              ]
            }
        """.trimIndent() + "\n"
    }
}
