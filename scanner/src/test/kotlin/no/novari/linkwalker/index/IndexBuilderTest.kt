package no.novari.linkwalker.index

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.NoDataException
import no.novari.linkwalker.NoRouteException
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.ScannerProperties
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class IndexBuilderTest {

    private val fintClient = mockk<FintClient>(relaxed = true)
    private val metamodel = mockk<MetamodelService>()
    private val extractor = mockk<RecordExtractor>(relaxed = true)

    private val scannerConfig = ScannerProperties(orgId = OrgId("test"), baseUrl = "https://api.test")
    private val httpConfig = HttpProperties(maxConcurrentFetches = 4)

    private val builder = IndexBuilder(scannerConfig, httpConfig, fintClient, metamodel, extractor)

    @Test
    fun `successful fetch indexes records`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns Unit
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/abc")), totalItems = 1)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
    }

    @Test
    fun `walks pages using total_items until fully drained`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns Unit
        // total_items = 250_000 with PAGE_SIZE = 100_000 → 3 fetches at offset 0, 100_000, 200_000.
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returnsMany listOf(
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/a")), totalItems = 250_000),
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/b")), totalItems = 250_000),
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/c")), totalItems = 250_000),
        )

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(3, index.records.size)
        coVerify(exactly = 1) { fintClient.streamToFile(match { it.contains("offset=0") }, any(), any()) }
        coVerify(exactly = 1) { fintClient.streamToFile(match { it.contains("offset=100000") }, any(), any()) }
        coVerify(exactly = 1) { fintClient.streamToFile(match { it.contains("offset=200000") }, any(), any()) }
    }

    @Test
    fun `stops on short page when total_items is absent`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns Unit
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            PageExtraction(listOf(record("https://api.test/foo/bar/baz/systemid/only")), totalItems = null)

        val index = builder.buildIndex(listOf("foo_bar"), "bearer")

        assertEquals(1, index.records.size)
        coVerify(exactly = 1) { fintClient.streamToFile(any(), any(), any()) }
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
        coEvery { fintClient.streamToFile(match { it.contains("/good?") }, any(), any()) } returns Unit
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
        }

        capBuilder.buildIndex(listOf("foo_bar"), "bearer")

        assertTrue(
            maxObserved.get() <= cap,
            "Max in-flight fetches ${maxObserved.get()} exceeded cap $cap",
        )
        assertTrue(maxObserved.get() > 1, "Test setup error: no concurrency observed")
    }

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
