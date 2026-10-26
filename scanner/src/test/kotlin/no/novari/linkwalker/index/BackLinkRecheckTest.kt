package no.novari.linkwalker.index

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.novari.fint.model.FintMultiplicity
import no.novari.fint.model.FintRelation
import no.novari.fint.model.resource.FintResource
import no.novari.linkwalker.FetchRouting
import no.novari.linkwalker.FintClient
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.config.BackLinkRecheckProperties
import no.novari.linkwalker.config.HttpProperties
import no.novari.linkwalker.config.IndexProperties
import no.novari.linkwalker.config.ScannerProperties
import no.novari.linkwalker.report.ProblemType
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Component
import no.novari.metamodel.model.Resource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper

class BackLinkRecheckTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val mapper = jacksonObjectMapper()
    private val indexConfig = IndexProperties()
    private val extractor = RecordExtractor(mapper, indexConfig)
    private val validator = IndexValidator(membershipMetamodel(), AutoRelationRules(indexConfig), HrefSanitizer(indexConfig))

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        base = server.url("/").toString().trimEnd('/')
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a target that points back by now has no problem after the recheck`() = runBlocking {
        val index = indexOf(membership(), elevforhold("systemid/e1"))
        server.enqueue(json(elevforholdJson("systemid/e1", pointsBack = true)))

        val refreshed = recheck().refresh(index, "t")

        assertEquals(1, validator.validate(ORG, index).size)
        assertTrue(validator.validate(ORG, refreshed).isEmpty())
        assertEquals("/utdanning/elev/elevforhold/systemid/e1", server.takeRequest().path)
    }

    @Test
    fun `a target that still does not point back keeps its problem`() = runBlocking {
        val index = indexOf(membership(), elevforhold("systemid/e1"))
        server.enqueue(json(elevforholdJson("systemid/e1", pointsBack = false)))

        val refreshed = recheck().refresh(index, "t")

        assertEquals(ProblemType.MissingBackLinkAdapter, validator.validate(ORG, refreshed).single().problemType)
    }

    @Test
    fun `a target that cannot be fetched keeps the version the scan read`() = runBlocking {
        val index = indexOf(membership(), elevforhold("systemid/e1"))
        server.enqueue(MockResponse().setResponseCode(404))

        val refreshed = recheck().refresh(index, "t")

        assertEquals(1, validator.validate(ORG, refreshed).size)
    }

    @Test
    fun `a target with only a personal key is not fetched`() = runBlocking {
        val index = indexOf(membership(target = "fodselsnummer/12345678901"), elevforhold("fodselsnummer/12345678901"))

        val refreshed = recheck().refresh(index, "t")

        assertEquals(0, server.requestCount)
        assertEquals(1, validator.validate(ORG, refreshed).size)
    }

    @Test
    fun `a limit of 0 turns the recheck off`() = runBlocking {
        val index = indexOf(membership(), elevforhold("systemid/e1"))

        recheck(maxRecords = 0).refresh(index, "t")

        assertEquals(0, server.requestCount)
    }

    private fun recheck(maxRecords: Int = 10): BackLinkRecheck {
        val properties = ScannerProperties(
            orgId = ORG.value,
            baseUrl = base,
            backLinkRecheck = BackLinkRecheckProperties(maxRecords = maxRecords, concurrency = 1),
        )
        val client = FintClient(RestClient.builder().build(), HttpProperties(maxAttempts = 0), FetchRouting(properties))
        return BackLinkRecheck(properties, validator, client, extractor, HrefSanitizer(indexConfig), mapper)
    }

    private fun membership(target: String = "systemid/e1"): MinimalRecord = record(
        "utdanning_vurdering", "eksamensgruppemedlemskap",
        """{"_links": {
             "self": [{"href": "$base/utdanning/vurdering/eksamensgruppemedlemskap/systemid/g1_e1"}],
             "elevforhold": [{"href": "$base/utdanning/elev/elevforhold/$target"}]
           }}""",
    )

    private fun elevforhold(key: String): MinimalRecord =
        record("utdanning_elev", "elevforhold", elevforholdJson(key, pointsBack = false))

    private fun elevforholdJson(key: String, pointsBack: Boolean): String {
        val back = if (pointsBack) {
            """, "eksamensgruppemedlemskap": [{"href": "$base/utdanning/vurdering/eksamensgruppemedlemskap/systemid/g1_e1"}]"""
        } else {
            ""
        }
        return """{"_links": {"self": [{"href": "$base/utdanning/elev/elevforhold/$key"}]$back}}"""
    }

    private fun record(component: String, resource: String, json: String): MinimalRecord =
        extractor.extract(mapper.readTree(json), component, resource)!!

    private fun indexOf(vararg records: MinimalRecord): TenantIndex {
        val byKey = records.flatMap { record -> record.canonicalKeys.map { it to record } }.toMap()
        val fetched = setOf(
            ResourceKey.of("utdanning_vurdering", "eksamensgruppemedlemskap"),
            ResourceKey.of("utdanning_elev", "elevforhold"),
        )
        return TenantIndex(records.toList(), byKey, fetched)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun membershipMetamodel(): MetamodelService = mockk {
        every { getResource(any(), any(), any()) } returns null
        every { getResource("utdanning", "vurdering", "eksamensgruppemedlemskap") } returns Resource(
            name = "eksamensgruppemedlemskap",
            component = Component(domainName = "utdanning", packageName = "vurdering"),
            className = "no.fint.model.utdanning.vurdering.Eksamensgruppemedlemskap",
            resourceClass = FintResource::class.java,
            isCommon = false,
            writeable = true,
            fields = emptySet(),
            idFields = setOf("systemId"),
            relations = listOf(relation("elevforhold", "eksamensgruppemedlemskap")),
        )
    }

    private fun relation(name: String, inverse: String) = object : FintRelation {
        override fun getPackageName() = "no.fint.model.utdanning.elev.$name"
        override fun getMultiplicity() = FintMultiplicity.ONE_TO_ONE
        override fun getName() = name
        override fun getInverseName() = inverse
    }

    private companion object {
        val ORG = OrgId("test_org")
    }
}
