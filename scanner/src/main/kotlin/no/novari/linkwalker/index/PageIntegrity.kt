package no.novari.linkwalker.index

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.inputStream

/**
 * Looks through a downloaded page for the traces the Access Gateway leaves when it damages a
 * body: NUL bytes, a URL that starts with two schemes, `httphttp`, a link that is only the public
 * host, and `${` placeholders that adapters send in and the API never serves. Also computes the
 * SHA-256 of the file, so one download of a page can be told apart from the next.
 */
class PageIntegrity(publicBaseUrl: String) {

    private val signatures: List<Signature> = listOf(
        Signature("doubled-scheme", "https:https://"),
        Signature("doubled-scheme", "https:http://"),
        Signature("doubled-scheme", "http:https://"),
        Signature("doubled-scheme", "http:http://"),
        Signature("doubled-scheme", "httphttp"),
        Signature("placeholder-href", "\"\${"),
        Signature("bare-host-href", "\"${publicBaseUrl.trimEnd('/')}\""),
    )
    private val carrySize: Int = signatures.maxOf { it.bytes.size } - 1

    fun inspect(file: Path): Report {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK)
        var carry = ByteArray(0)
        var position = 0L
        var finding: Finding? = null

        file.inputStream().buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                if (finding == null) {
                    val window = carry + buffer.copyOf(read)
                    finding = firstFinding(window, windowStart = position - carry.size)
                    carry = window.copyOfRange(maxOf(0, window.size - carrySize), window.size)
                }
                position += read
            }
        }
        return Report(bytes = position, sha256 = digest.digest().toHex(), finding = finding)
    }

    private fun firstFinding(window: ByteArray, windowStart: Long): Finding? {
        var best: Finding? = null
        val nul = window.indexOf(0.toByte())
        if (nul >= 0) best = Finding("nul-byte", windowStart + nul)
        for (signature in signatures) {
            val index = window.indexOf(signature.bytes)
            if (index >= 0 && (best == null || windowStart + index < best.offset)) {
                best = Finding(signature.name, windowStart + index)
            }
        }
        return best
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        val first = needle[0]
        var i = 0
        val last = size - needle.size
        while (i <= last) {
            if (this[i] == first) {
                var j = 1
                while (j < needle.size && this[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private class Signature(val name: String, text: String) {
        val bytes: ByteArray = text.toByteArray(Charsets.ISO_8859_1)
    }

    data class Finding(
        val signature: String,
        val offset: Long,
    )

    data class Report(
        val bytes: Long,
        val sha256: String,
        val finding: Finding?,
    )

    private companion object {
        const val CHUNK = 64 * 1024
    }
}
