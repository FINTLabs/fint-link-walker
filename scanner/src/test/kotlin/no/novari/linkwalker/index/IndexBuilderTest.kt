package no.novari.linkwalker.index

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.FetchResult
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.NoDataException
import no.novari.linkwalker.NoRouteException
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.ScannerProperties
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Resource
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeBytes

class IndexBuilderTest {

    private val fintClient = mockk<FintClient>(relaxed = true)
    private val metamodel = mockk<MetamodelService>()
    private val extractor = mockk<RecordExtractor>(relaxed = true)

    private val scannerConfig = ScannerProperties(orgId = "test", baseUrl = "https://api.test")
    private val httpConfig = HttpProperties(maxConcurrentFetches = 4)

    private val fetched = FetchResult(bytes = 0, via = null, route = "gateway")

    private val builder = IndexBuilder(scannerConfig, httpConfig, fintClient, metamodel, extractor)

    @Test
    fun `successful fetch indexes records`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/abc")), totalItems = 1)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
    }

    @Test
    fun `first page is requested with the configured page size and no cursor`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/only")), totalItems = 1)

        builder.buildIndex(listOf("foo_bar"), "bearer")

        coVerify(exactly = 1) { fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000", any(), any()) }
    }

    @Test
    fun `per-resource page size overrides the default and matches case-insensitively`() = runBlocking {
        val sizedConfig = ScannerProperties(orgId = "test", baseUrl = "https://api.test", pageSizes = mapOf("Skole" to 3))
        val sizedBuilder = IndexBuilder(sizedConfig, httpConfig, fintClient, metamodel, extractor)
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("skole"), fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", any()) } returns PageExtraction(emptyList(), totalItems = 0)

        sizedBuilder.buildIndex(listOf("foo_bar"), "bearer")

        coVerify(exactly = 1) { fintClient.streamToFile("https://api.test/foo/bar/skole?size=3", any(), any()) }
        coVerify(exactly = 1) { fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000", any(), any()) }
    }

    @Test
    fun `follows next links in order until a page has none`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returnsMany listOf(
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/a")),
                totalItems = 3,
                nextHref = "https://api.test/foo/bar/baz?size=10000&cursor=p2",
            ),
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/b")),
                totalItems = 3,
                nextHref = "https://api.test/foo/bar/baz?size=10000&cursor=p3",
            ),
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/c")),
                totalItems = 3,
                nextHref = null,
            ),
        )

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(3, index.records.size)
        coVerifyOrder {
            fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000", any(), any())
            fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000&cursor=p2", any(), any())
            fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000&cursor=p3", any(), any())
        }
        coVerify(exactly = 3) { fintClient.streamToFile(any(), any(), any()) }
    }

    @Test
    fun `relative next link is resolved against the base url`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returnsMany listOf(
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/a")),
                totalItems = 2,
                nextHref = "/foo/bar/baz?size=10000&cursor=p2",
            ),
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/b")), totalItems = 2),
        )

        builder.buildIndex(listOf("foo_bar"), "bearer")

        coVerify(exactly = 1) {
            fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000&cursor=p2", any(), any())
        }
    }

    @Test
    fun `a next link that was already fetched fails the scan`() {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/a")),
                totalItems = null,
                nextHref = "https://api.test/foo/bar/baz?size=10000&cursor=p2",
            )

        assertThrows(IncompleteIndexException::class.java) {
            runBlocking { builder.buildIndex(listOf("foo_bar"), "bearer") }
        }
        coVerify(exactly = 2) { fintClient.streamToFile(any(), any(), any()) }
    }

    @Test
    fun `running out of next links before total_items is reached fails the scan`() {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/a")),
                totalItems = 250_000,
                nextHref = null,
                entryCount = 10_000,
            )

        val ex = assertThrows(IncompleteIndexException::class.java) {
            runBlocking { builder.buildIndex(listOf("foo_bar"), "bearer") }
        }
        assertTrue(ex.message!!.contains("10000 of 250000"), ex.message)
    }

    @Test
    fun `entries dropped at extraction still count toward total_items`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(emptyList(), totalItems = 2, nextHref = null, entryCount = 2)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `missing total_items skips the completeness check`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/only")), totalItems = null)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
        coVerify(exactly = 1) { fintClient.streamToFile(any(), any(), any()) }
    }

    @Test
    fun `a page that fails to parse is fetched again`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } throws brokenJson() andThen
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/a")), totalItems = 1)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
        coVerify(exactly = 2) { fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000", any(), any()) }
    }

    @Test
    fun `a page that never parses fails the scan after max attempts`() {
        val twoAttempts = HttpProperties(maxConcurrentFetches = 4, maxAttempts = 2)
        val strictBuilder = IndexBuilder(scannerConfig, twoAttempts, fintClient, metamodel, extractor)
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } throws brokenJson()

        val ex = assertThrows(PageParseException::class.java) {
            runBlocking { strictBuilder.buildIndex(listOf("foo_bar"), "bearer") }
        }
        assertTrue(ex.message!!.contains("https://api.test/foo/bar/baz?size=10000"), ex.message)
        assertTrue(ex.message!!.contains("2 attempts"), ex.message)
        coVerify(exactly = 2) { fintClient.streamToFile(any(), any(), any()) }
    }

    @Test
    fun `NoRouteException returns empty for the resource (normal not-deployed case)`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws NoRouteException("not deployed")

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `NoDataException returns empty for the resource (normal cache-miss case)`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws NoDataException("cache miss")

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `unexpected exception fails the whole scan (fail-fast, no partial report)`() {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws RuntimeException("boom")

        val ex = assertThrows(RuntimeException::class.java) {
            runBlocking { builder.buildIndex(listOf("foo_bar"), "bearer") }
        }
        assertEquals("boom", ex.message)
    }

    @Test
    fun `one bad resource cancels all sibling fetches and the build fails`() {
        every { metamodel.getResources("foo", "bar") } returns listOf(
            fakeResource("good"),
            fakeResource("bad"),
        )
        coEvery { fintClient.streamToFile(match { it.contains("/good?") }, any(), any()) } returns fetched
        coEvery { fintClient.streamToFile(match { it.contains("/bad?") }, any(), any()) } throws
            RuntimeException("boom")
        every { extractor.extractFromFile(any(), "foo_bar", "good") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/good/systemid/g-1")), totalItems = 1)

        assertThrows(RuntimeException::class.java) {
            runBlocking { builder.buildIndex(listOf("foo_bar"), "bearer") }
        }
    }

    @Test
    fun `unparseable component name is skipped without failing the build`() = runBlocking {
        val index = builder.buildIndex(listOf("singleword"), "bearer")
        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `component with no resources in metamodel is skipped`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns emptyList()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer")
        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `parallelism cap is honored when many resources fan out concurrently`() = runBlocking {
        // 20 resources × cap 4 = at most 4 fetches in flight at any moment.
        // Regression test for the dispatcher-cap bug where withContext(Dispatchers.IO)
        // inside the fetch path silently defeated the limited dispatcher.
        // Note: uses Thread.sleep, not delay, to model real blocking I/O (Files.copy).
        // limitedParallelism caps actively-running coroutines; suspending releases the slot.
        val cap = 4
        val resources = (1..20).map { "res-$it" }
        val capConfig = HttpProperties(maxConcurrentFetches = cap)
        val capBuilder = IndexBuilder(scannerConfig, capConfig, fintClient, metamodel, extractor)

        val inFlight = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)

        every { metamodel.getResources("foo", "bar") } returns resources.map { fakeResource(it) }
        every { extractor.extractFromFile(any(), any(), any()) } returns
            PageExtraction(emptyList(), totalItems = 0)
        coEvery { fintClient.streamToFile(any(), any(), any()) } coAnswers {
            val now = inFlight.incrementAndGet()
            maxObserved.updateAndGet { prev -> if (now > prev) now else prev }
            Thread.sleep(30)
            inFlight.decrementAndGet()
            fetched
        }

        capBuilder.buildIndex(listOf("foo_bar"), "bearer")

        assertTrue(
            maxObserved.get() <= cap,
            "Max in-flight fetches ${maxObserved.get()} exceeded cap $cap",
        )
        assertTrue(maxObserved.get() > 1, "Test setup error: no concurrency observed")
    }


    @Test
    fun `a damaged page is fetched again and the clean copy is indexed`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } coAnswers {
            thirdArg<Path>().writeBytes("""{"href":"https:https://api.test/foo/bar/baz/systemid/a"}""".toByteArray())
            fetched
        } andThenAnswer {
            thirdArg<Path>().writeBytes("""{"href":"https://api.test/foo/bar/baz/systemid/a"}""".toByteArray())
            fetched
        }
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/a")), totalItems = 1)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
        coVerify(exactly = 2) { fintClient.streamToFile("https://api.test/foo/bar/baz?size=10000", any(), any()) }
    }

    @Test
    fun `a page that stays damaged fails the scan after max attempts`() {
        val twoAttempts = HttpProperties(maxConcurrentFetches = 4, maxAttempts = 2)
        val strictBuilder = IndexBuilder(scannerConfig, twoAttempts, fintClient, metamodel, extractor)
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } coAnswers {
            thirdArg<Path>().writeBytes("""{"a":"x""".toByteArray() + byteArrayOf(0) + """"}""".toByteArray())
            FetchResult(bytes = 10, via = "1.1 gw (Access Gateway-ag-9)", route = "gateway")
        }

        val ex = assertThrows(PageCorruptException::class.java) {
            runBlocking { strictBuilder.buildIndex(listOf("foo_bar"), "bearer") }
        }
        assertTrue(ex.message!!.contains("nul-byte at byte 7"), ex.message)
        assertTrue(ex.message!!.contains("2 attempts"), ex.message)
        assertTrue(ex.message!!.contains("ag-9"), ex.message)
        coVerify(exactly = 2) { fintClient.streamToFile(any(), any(), any()) }
        verify(exactly = 0) { extractor.extractFromFile(any(), any(), any()) }
    }

    @Test
    fun `entries with a malformed self href are counted and the page still indexes the rest`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns fetched
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(
                listOf(record("https://api.test/foo/bar/baz/systemid/a")),
                totalItems = 2,
                entryCount = 2,
                malformedSelfCount = 1,
            )

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
    }

    private fun brokenJson(): JacksonException =
        runCatching { jacksonObjectMapper().readTree("{\"a\":\"\u0000\"}") }
            .exceptionOrNull() as JacksonException

    private fun fakeResource(name: String): Resource = mockk(relaxed = true) {
        every { this@mockk.name } returns name
    }

    private fun record(self: String): MinimalRecord = MinimalRecord(
        component = "foo_bar",
        resourceName = "baz",
        canonicalKeys = listOf(self),
        outboundRefs = emptyList(),
        malformedHrefs = emptyList(),
    )
}
