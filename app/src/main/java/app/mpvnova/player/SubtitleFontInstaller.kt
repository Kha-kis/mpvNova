package app.mpvnova.player

import java.io.File
import java.io.InputStream

internal fun installSubtitleFont(input: InputStream, destination: File): File? {
    destination.parentFile?.mkdirs()
    val temporary = File(destination.parentFile, ".${destination.name}.tmp")
    var installed: File? = null
    try {
        val withinLimit = temporary.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var read = input.read(buffer)
            while (read >= 0 && total + read <= SubtitleFontTable.MAX_FONT_FILE_BYTES) {
                output.write(buffer, 0, read)
                total += read
                read = input.read(buffer)
            }
            read < 0 && total > 0L
        }
        if (withinLimit && SubtitleFontTable.familyName(temporary) != null) {
            val moved = temporary.renameTo(destination) ||
                (destination.delete() && temporary.renameTo(destination))
            if (moved)
                installed = destination
        }
    } finally {
        temporary.delete()
    }
    return installed
}
