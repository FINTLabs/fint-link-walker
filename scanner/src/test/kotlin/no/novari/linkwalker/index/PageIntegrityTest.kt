package no.novari.linkwalker.index

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.writeBytes

class PageIntegrityTest {

    @TempDir
    lateinit var tmp: Path

    private val integrity = PageIntegrity("https://beta.example")

    @Test
    fun `a clean page has no finding and a matching hash and size`() {
        val body = """{"_links":{"self":[{"href":"https://beta.example/utdanning/elev/elev/systemid/a"}]}}"""
        val file = page(body.toByteArray())

        val report = integrity.inspect(file)

        assertNull(report.finding)
        assertEquals(body.length.toLong(), report.bytes)
        assertEquals(sha256(body.toByteArray()), report.sha256)
    }

    @Test
    fun `a NUL byte is found at its offset`() {
        val file = page("""{"a":"x""".toByteArray() + byteArrayOf(0, 0) + """"}""".toByteArray())

        val finding = integrity.inspect(file).finding

        assertEquals(PageIntegrity.Finding("nul-byte", 7), finding)
    }

    @Test
    fun `a link with two schemes is found`() {
        val file = page("""{"href":"https:https://beta.exampleample/utdanning/elev/elev/systemid/a"}""".toByteArray())

        assertEquals(PageIntegrity.Finding("doubled-scheme", 9), integrity.inspect(file).finding)
    }

    @Test
    fun `a link starting with httphttps is found`() {
        val file = page("""{"href":"httphttps://beta.example"}""".toByteArray())

        assertEquals(PageIntegrity.Finding("doubled-scheme", 9), integrity.inspect(file).finding)
    }

    @Test
    fun `a placeholder link is found`() {
        val file = page("""{"href":"${'$'}{no.novari.fint.model.utdanning.elev.klasse}/systemid/1"}""".toByteArray())

        assertEquals(PageIntegrity.Finding("placeholder-href", 8), integrity.inspect(file).finding)
    }

    @Test
    fun `a link that is only the public host is found but a full link is not`() {
        val bare = page("""{"href":"https://beta.example"}""".toByteArray())
        val full = page("""{"href":"https://beta.example/utdanning/elev/elev/systemid/a"}""".toByteArray())

        assertEquals(PageIntegrity.Finding("bare-host-href", 8), integrity.inspect(bare).finding)
        assertNull(integrity.inspect(full).finding)
    }

    @Test
    fun `a signature that straddles the 64 KiB read boundary is still found`() {
        val prefix = ByteArray(65_530) { 'a'.code.toByte() }
        val file = page(prefix + "https:https://x".toByteArray())

        assertEquals(PageIntegrity.Finding("doubled-scheme", 65_530), integrity.inspect(file).finding)
    }

    @Test
    fun `the earliest damage in the page is the one reported`() {
        val file = page(("""{"a":"httphttp","b":"""".toByteArray() + byteArrayOf(0) + """"}""".toByteArray()))

        assertEquals(PageIntegrity.Finding("doubled-scheme", 6), integrity.inspect(file).finding)
    }

    @Test
    fun `an empty file is clean`() {
        val file = page(ByteArray(0))

        val report = integrity.inspect(file)

        assertNull(report.finding)
        assertEquals(0L, report.bytes)
    }

    private fun page(bytes: ByteArray): Path = tmp.resolve("page-${bytes.size}-${bytes.hashCode()}.json").also { it.writeBytes(bytes) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
