package no.novari.linkwalker.index

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.NoDataException
import no.novari.linkwalker.NoRouteException
import no.novari.linkwalker.config.LinkWalkerConfig
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndexBuilderTest {

    private val fintClient = mockk<FintClient>(relaxed = true)
    private val metamodel = mockk<MetamodelService>()
    private val extractor = mockk<RecordExtractor>(relaxed = true)

    private val config = LinkWalkerConfig(
        tenant = "test",
        baseUrl = "https://api.test",
        fetchConcurrency = 2,
    )

    private val builder = IndexBuilder(config, fintClient, metamodel, extractor)

    @Test
    fun `successful fetch indexes records`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } returns Unit
        every { extractor.extractFromFile(any(), "foo_bar", "baz") } returns
            listOf(record("https://api.test/foo/bar/baz/systemid/abc"))

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertEquals(1, index.records.size)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `NoRouteException returns empty without onComponentError`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws NoRouteException("not deployed")

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertTrue(index.records.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `NoDataException returns empty without onComponentError`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws NoDataException("cache miss")

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertTrue(index.records.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `generic exception returns empty and calls onComponentError`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(fakeResource("baz"))
        coEvery { fintClient.streamToFile(any(), any(), any()) } throws RuntimeException("boom")

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertTrue(index.records.isEmpty())
        assertEquals(listOf("foo_bar"), errors)
    }

    @Test
    fun `unparseable component name is skipped without errors`() = runBlocking {
        // metamodel never queried because parsing fails first
        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("singleword"), "bearer") { errors += it }

        assertTrue(index.records.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `component with no resources in metamodel is skipped without errors`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns emptyList()

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertTrue(index.records.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `mixed success and failure across resources is handled per resource`() = runBlocking {
        every { metamodel.getResources("foo", "bar") } returns listOf(
            fakeResource("good"),
            fakeResource("bad"),
        )
        coEvery { fintClient.streamToFile(match { it.endsWith("/good") }, any(), any()) } returns Unit
        coEvery { fintClient.streamToFile(match { it.endsWith("/bad") }, any(), any()) } throws
            RuntimeException("boom")
        every { extractor.extractFromFile(any(), "foo_bar", "good") } returns
            listOf(record("https://api.test/foo/bar/good/systemid/g-1"))

        val errors = mutableListOf<String>()
        val index = builder.buildIndex(listOf("foo_bar"), "bearer") { errors += it }

        assertEquals(1, index.records.size)
        assertEquals(listOf("foo_bar"), errors)
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
