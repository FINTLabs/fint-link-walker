package no.novari.linkwalker.index

import tools.jackson.core.JacksonException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream

object PageDiagnostics {

    fun describe(file: Path, ex: JacksonException): String {
        val size = runCatching { Files.size(file) }.getOrDefault(-1L)
        val offset = ex.location?.byteOffset ?: -1L
        val zeros = runCatching { zeroBytes(file) }.getOrNull()
        return buildString {
            append("parse error at byte ").append(offset).append(" of ").append(size)
            if (zeros != null) {
                append(", ").append(zeros.count).append(" zero bytes in file")
                if (zeros.count > 0) {
                    append(", first run of ").append(zeros.firstRunLength)
                        .append(" at byte ").append(zeros.firstOffset)
                }
            }
            append(": ").append(ex.originalMessage)
        }
    }

    data class ZeroBytes(val count: Long, val firstOffset: Long, val firstRunLength: Long)

    fun zeroBytes(file: Path): ZeroBytes {
        var count = 0L
        var firstOffset = -1L
        var firstRunLength = 0L
        var inFirstRun = false
        var position = 0L
        val buffer = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                for (i in 0 until read) {
                    if (buffer[i] == 0.toByte()) {
                        count++
                        if (firstOffset < 0) {
                            firstOffset = position + i
                            inFirstRun = true
                        }
                        if (inFirstRun) firstRunLength++
                    } else {
                        inFirstRun = false
                    }
                }
                position += read
            }
        }
        return ZeroBytes(count, firstOffset, firstRunLength)
    }
}
