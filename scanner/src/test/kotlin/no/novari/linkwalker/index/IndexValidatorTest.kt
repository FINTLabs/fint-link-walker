package no.novari.linkwalker.index

import io.mockk.every
import io.mockk.mockk
import no.novari.fint.model.FintMultiplicity
import no.novari.fint.model.FintRelation
import no.novari.fint.model.resource.FintResource
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.config.AutoRelationRule
import no.novari.linkwalker.config.IndexProperties
import no.novari.linkwalker.report.ProblemType
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Component
import no.novari.metamodel.model.Resource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndexValidatorTest {

    @Test
    fun `bidirectional relation with matching back-link → no rows`() {
        val source = recordOf(
            component = "utdanning_vurdering",
            resource = "elevfravar",
            self = "https://host/utdanning/vurdering/elevfravar/systemid/abc",
            outboundRelation = "elev",
            outboundHref = "https://host/utdanning/elev/elev/systemid/elev-1",
        )
        val target = recordOf(
            component = "utdanning_elev",
            resource = "elev",
            self = "https://host/utdanning/elev/elev/systemid/elev-1",
            backLinkRelation = "fravarsregistrering",
            backLinkHref = "https://host/utdanning/vurdering/elevfravar/systemid/abc",
        )
        val index = indexOf(source, target)
        val metamodel = metamodelWith(
            "utdanning" to "vurdering" to "elevfravar" via "elev" inverse "fravarsregistrering",
            "utdanning" to "elev" to "elev" via "fravarsregistrering" inverse "elev",
        )

        val rows = validator(metamodel).validate(OrgId("afk_no"), index)

        assertTrue(rows.isEmpty(), "Expected no rows, got: $rows")
    }

    @Test
    fun `missing back-link without an auto-relation rule → missing-back-link-adapter`() {
        val source = recordOf(
            component = "utdanning_vurdering",
            resource = "elevfravar",
            self = "https://host/utdanning/vurdering/elevfravar/systemid/abc",
            outboundRelation = "elev",
            outboundHref = "https://host/utdanning/elev/elev/systemid/elev-1",
        )
        val target = recordOf(
            component = "utdanning_elev",
            resource = "elev",
            self = "https://host/utdanning/elev/elev/systemid/elev-1",
        )
        val index = indexOf(source, target)
        val metamodel = metamodelWith(
            "utdanning" to "vurdering" to "elevfravar" via "elev" inverse "fravarsregistrering",
        )

        val rows = validator(metamodel).validate(OrgId("afk_no"), index)

        val row = rows.single()
        assertEquals(ProblemType.MissingBackLinkAdapter, row.problemType)
        assertEquals("elev", row.relationName)
        assertEquals("fravarsregistrering", row.expectedInverseName)
    }

    @Test
    fun `missing back-link covered by auto-relation rule → missing-back-link-autorelation`() {
        val source = recordOf(
            component = "utdanning_vurdering",
            resource = "fravarsregistrering",
            self = "https://host/utdanning/vurdering/fravarsregistrering/systemid/abc",
            outboundRelation = "elevfravar",
            outboundHref = "https://host/utdanning/vurdering/elevfravar/systemid/ef-1",
        )
        val target = recordOf(
            component = "utdanning_vurdering",
            resource = "elevfravar",
            self = "https://host/utdanning/vurdering/elevfravar/systemid/ef-1",
        )
        val index = indexOf(source, target)
        val metamodel = metamodelWith(
            "utdanning" to "vurdering" to "fravarsregistrering" via "elevfravar" inverse "fravarsregistrering",
        )
        val rules = listOf(
            AutoRelationRule(
                source = "utdanning-vurdering-fravarsregistrering",
                relation = "elevfravar",
                target = "utdanning-vurdering-elevfravar",
                backRelation = "fravarsregistrering",
            ),
        )

        val rows = validator(
            metamodel,
            autoRelations = rules,
            autoRelationComponents = listOf("utdanning_vurdering"),
        ).validate(OrgId("afk_no"), index)

        assertEquals(ProblemType.MissingBackLinkAutorelation, rows.single().problemType)
    }

    @Test
    fun `report row prefers systemid over fodselsnummer when target has both`() {
        val source = recordOf(
            component = "utdanning_elev",
            resource = "elev",
            self = "https://host/utdanning/elev/elev/systemid/source-1",
            outboundRelation = "person",
            outboundHref = "https://host/utdanning/elev/person/fodselsnummer/12345678901",
        )
        val target = recordOf(
            component = "utdanning_elev",
            resource = "person",
            self = "https://host/utdanning/elev/person/systemid/p-1",
            extraSelfHrefs = listOf("https://host/utdanning/elev/person/fodselsnummer/12345678901"),
            // No back-link to source
        )
        val index = indexOf(source, target)
        val metamodel = metamodelWith(
            "utdanning" to "elev" to "elev" via "person" inverse "elev",
        )

        val rows = validator(metamodel).validate(OrgId("afk_no"), index)

        val row = rows.single()
        assertEquals("https://host/utdanning/elev/person/systemid/p-1", row.targetHref)
        assertEquals("https://host/utdanning/elev/elev/systemid/source-1", row.sourceSelf)
    }

    @Test
    fun `missing-resource pointing at fodselsnummer href has the value masked`() {
        val source = recordOf(
            component = "utdanning_elev",
            resource = "elev",
            self = "https://host/utdanning/elev/elev/systemid/source-1",
            outboundRelation = "person",
            outboundHref = "https://host/utdanning/elev/person/fodselsnummer/12345678901",
        )
        val index = indexOf(source)
        val metamodel = metamodelWith(
            "utdanning" to "elev" to "elev" via "person" inverse null,
        )

        val rows = validator(metamodel).validate(OrgId("afk_no"), index)

        val row = rows.single()
        assertEquals(ProblemType.MissingResource, row.problemType)
        assertEquals("https://host/utdanning/elev/person/fodselsnummer/***", row.targetHref)
    }

    @Test
    fun `outbound ref to a resource that does not exist → missing-resource`() {
        val source = recordOf(
            component = "utdanning_vurdering",
            resource = "elevfravar",
            self = "https://host/utdanning/vurdering/elevfravar/systemid/abc",
            outboundRelation = "elev",
            outboundHref = "https://host/utdanning/elev/elev/systemid/missing",
        )
        val index = indexOf(source)
        val metamodel = metamodelWith(
            "utdanning" to "vurdering" to "elevfravar" via "elev" inverse null,
        )

        val rows = validator(metamodel).validate(OrgId("afk_no"), index)

        assertEquals(1, rows.size)
        assertEquals(ProblemType.MissingResource, rows.single().problemType)
    }

    private fun validator(
        metamodel: MetamodelService,
        autoRelations: List<AutoRelationRule> = emptyList(),
        autoRelationComponents: List<String> = emptyList(),
        piiIdentifiers: List<String> = listOf("fodselsnummer", "feidenavn"),
    ): IndexValidator {
        val config = IndexProperties(
            autoRelations = autoRelations,
            autoRelationComponents = autoRelationComponents,
            piiIdentifiers = piiIdentifiers,
        )
        return IndexValidator(metamodel, AutoRelationRules(config), HrefSanitizer(config))
    }

    private fun indexOf(vararg records: MinimalRecord): TenantIndex {
        val byKey = records.flatMap { rec -> rec.canonicalKeys.map { it to rec } }.toMap()
        return TenantIndex(records = records.toList(), byKey = byKey)
    }

    private fun recordOf(
        component: String,
        resource: String,
        self: String,
        extraSelfHrefs: List<String> = emptyList(),
        outboundRelation: String? = null,
        outboundHref: String? = null,
        backLinkRelation: String? = null,
        backLinkHref: String? = null,
    ): MinimalRecord {
        val refs = mutableListOf<OutboundRef>()
        if (outboundRelation != null && outboundHref != null) {
            refs += OutboundRef(outboundRelation, outboundHref.canonicalize())
        }
        if (backLinkRelation != null && backLinkHref != null) {
            refs += OutboundRef(backLinkRelation, backLinkHref.canonicalize())
        }
        val canonicalKeys = (listOf(self) + extraSelfHrefs).map { it.canonicalize() }
        return MinimalRecord(
            component = component,
            resourceName = resource,
            canonicalKeys = canonicalKeys,
            outboundRefs = refs,
            malformedHrefs = emptyList(),
        )
    }

    private fun String.canonicalize(): String = trim().trimEnd('/').lowercase()

    private fun metamodelWith(vararg specs: ResourceSpec): MetamodelService = mockk {
        every { getResource(any(), any(), any()) } returns null
        specs.forEach { spec ->
            every { getResource(spec.domain, spec.pkg, spec.resourceName) } returns
                resourceWithRelation(spec.resourceName, spec.relation, spec.inverse)
        }
    }

    private fun resourceWithRelation(name: String, relationName: String, inverseName: String?): Resource {
        val relation = object : FintRelation {
            override fun getPackageName() = "no.fint.model.test.${relationName.lowercase()}"
            override fun getMultiplicity() = FintMultiplicity.ONE_TO_MANY
            override fun getName() = relationName
            override fun getInverseName() = inverseName
        }
        return Resource(
            name = name,
            component = Component(domainName = "test", packageName = "test"),
            className = "no.fint.model.test.$name",
            resourceClass = FintResource::class.java,
            isCommon = false,
            writeable = true,
            fields = emptySet(),
            idFields = setOf("systemId"),
            relations = listOf(relation),
        )
    }

    private data class ResourceSpec(
        val domain: String,
        val pkg: String,
        val resourceName: String,
        val relation: String,
        val inverse: String?,
    )

    private infix fun Pair<Pair<String, String>, String>.via(relation: String) =
        Triple(first.first, first.second, second) to relation

    private infix fun Pair<Triple<String, String, String>, String>.inverse(inverseName: String?): ResourceSpec =
        ResourceSpec(
            domain = first.first,
            pkg = first.second,
            resourceName = first.third,
            relation = second,
            inverse = inverseName,
        )
}
