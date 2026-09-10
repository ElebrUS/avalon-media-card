package org.ensodai.avalonmediacard.plugins.samsungtv.domain

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TizenWidgetPackager {
    fun pack(files: Map<String, ByteArray>): ByteArray {
        require(files.containsKey("config.xml")) { "Tizen widget must contain config.xml" }
        require(files.containsKey("index.html")) { "Tizen widget must contain index.html" }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (path, bytes) ->
                val normalized = path.trimStart('/').replace('\\', '/')
                zip.putNextEntry(ZipEntry(normalized))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
