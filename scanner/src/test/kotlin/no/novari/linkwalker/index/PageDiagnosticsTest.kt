package no.novari.linkwalker.index

import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PageDiagnosticsTest {

    @Test
    fun `counts zero bytes and the first run without quoting content`(@TempDir tmp: Path) {
        val file = tmp.resolve("page.json")
        val prefix = """{"total_items":1,"_embedded":{"_entries":[{"name":"Ola"""
        Files.write(file, prefix.toByteArray() + byteArrayOf(0, 0, 0) + """"}]}}""".toByteArray() + byteArrayOf(0))

        val ex = runCatching { jacksonObjectMapper().readTree(Files.newInputStream(file)) }
            .exceptionOrNull() as JacksonException
        val text = PageDiagnostics.describe(file, ex)

        assertTrue(text.contains("4 zero bytes in file"), text)
        assertTrue(text.contains("first run of 3 at byte ${prefix.length}"), text)
        assertTrue(text.contains("of ${Files.size(file)}"), text)
        assertTrue(!text.contains("Ola"), text)
    }

    @Test
    fun `reports no zero bytes for a clean file`(@TempDir tmp: Path) {
        val file = tmp.resolve("page.json")
        Files.writeString(file, "{")

        val ex = runCatching { jacksonObjectMapper().readTree(Files.newInputStream(file)) }
            .exceptionOrNull() as JacksonException

        assertEquals(PageDiagnostics.ZeroBytes(0, -1, 0), PageDiagnostics.zeroBytes(file))
        assertTrue(PageDiagnostics.describe(file, ex).contains("0 zero bytes in file"))
    }
}
