package no.novari.linkwalker.index

import io.mockk.every
import io.mockk.mockk
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.config.IndexProperties
import no.novari.linkwalker.report.ProblemType
import no.novari.metamodel.MetamodelService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiiMaskingFlowTest {

    private val metamodel = mockk<MetamodelService>(relaxed = true)
    private val rules = mockk<AutoRelationRules> {
        every { isAutoRelation(any(), any(), any()) } returns false
    }
    private val sanitizer = HrefSanitizer(IndexProperties())
    private val validator = IndexValidator(metamodel, rules, sanitizer)

    @Test
    fun `missing-resource ReportRow masks PII fodselsnummer in source and target hrefs`() {
        val sourceFnrHref = "https://api/administrasjon/personal/personalressurs/fodselsnummer/12345678901"
        val targetFnrHref = "https://api/administrasjon/personal/person/fodselsnummer/12345678901"

        val source = MinimalRecord(
            component = "administrasjon_personal",
            resourceName = "personalressurs",
            canonicalKeys = listOf(sourceFnrHref),
            outboundRefs = listOf(OutboundRef(relationName = "person", targetCanonical = targetFnrHref)),
            malformedHrefs = emptyList(),
        )
        val index = TenantIndex(records = listOf(source), byKey = mapOf(sourceFnrHref to source))

        val rows = validator.validate(OrgId("afk_no"), index)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(ProblemType.MissingResource, row.problemType)
        assertTrue(row.sourceSelf.endsWith("/fodselsnummer/***"), "sourceSelf must mask PII value: ${row.sourceSelf}")
        assertTrue(row.targetHref.endsWith("/fodselsnummer/***"), "targetHref must mask PII value: ${row.targetHref}")
        assertFalse("12345678901" in row.sourceSelf, "fodselsnummer leaked in sourceSelf")
        assertFalse("12345678901" in row.targetHref, "fodselsnummer leaked in targetHref")
    }

    @Test
    fun `record with both PII and non-PII keys prefers non-PII for sourceSelf`() {
        val systemIdHref = "https://api/administrasjon/personal/personalressurs/systemid/abc-123"
        val fnrHref = "https://api/administrasjon/personal/personalressurs/fodselsnummer/12345678901"
        val targetHref = "https://api/administrasjon/personal/person/systemid/missing"

        val source = MinimalRecord(
            component = "administrasjon_personal",
            resourceName = "personalressurs",
            canonicalKeys = listOf(systemIdHref, fnrHref),
            outboundRefs = listOf(OutboundRef(relationName = "person", targetCanonical = targetHref)),
            malformedHrefs = emptyList(),
        )
        val index = TenantIndex(records = listOf(source), byKey = mapOf(systemIdHref to source))

        val rows = validator.validate(OrgId("afk_no"), index)

        assertEquals(1, rows.size)
        assertEquals(systemIdHref, rows.single().sourceSelf, "Should prefer non-PII canonical key over fodselsnummer")
    }

    @Test
    fun `unknown-link masks PII in malformed href`() {
        val sourceHref = "https://api/utdanning/elev/elev/systemid/abc"
        val malformedFnrHref = "https://api/administrasjon/personal/person/fodselsnummer/12345678901"

        val source = MinimalRecord(
            component = "utdanning_elev",
            resourceName = "elev",
            canonicalKeys = listOf(sourceHref),
            outboundRefs = emptyList(),
            malformedHrefs = listOf(malformedFnrHref),
        )
        val index = TenantIndex(records = listOf(source), byKey = mapOf(sourceHref to source))

        val rows = validator.validate(OrgId("afk_no"), index)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(ProblemType.UnknownLink, row.problemType)
        assertFalse("12345678901" in row.targetHref, "fodselsnummer leaked in malformed targetHref: ${row.targetHref}")
    }

    @Test
    fun `feidenavn is masked just like fodselsnummer`() {
        val feideHref = "https://api/utdanning/elev/person/feidenavn/alice@example.no"
        val targetHref = "https://api/utdanning/elev/elev/feidenavn/alice@example.no"

        val source = MinimalRecord(
            component = "utdanning_elev",
            resourceName = "person",
            canonicalKeys = listOf(feideHref),
            outboundRefs = listOf(OutboundRef(relationName = "elev", targetCanonical = targetHref)),
            malformedHrefs = emptyList(),
        )
        val index = TenantIndex(records = listOf(source), byKey = mapOf(feideHref to source))

        val rows = validator.validate(OrgId("afk_no"), index)

        assertEquals(1, rows.size)
        assertFalse("alice" in rows.single().sourceSelf, "feidenavn leaked in sourceSelf: ${rows.single().sourceSelf}")
        assertFalse("alice" in rows.single().targetHref, "feidenavn leaked in targetHref: ${rows.single().targetHref}")
    }

    @Test
    fun `index lookup still resolves by unmasked PII canonical key (validation accuracy preserved)`() {
        val fnrHref = "https://api/administrasjon/personal/person/fodselsnummer/12345678901"

        val target = MinimalRecord(
            component = "administrasjon_personal",
            resourceName = "person",
            canonicalKeys = listOf(fnrHref),
            outboundRefs = emptyList(),
            malformedHrefs = emptyList(),
        )
        val source = MinimalRecord(
            component = "administrasjon_personal",
            resourceName = "personalressurs",
            canonicalKeys = listOf("https://api/administrasjon/personal/personalressurs/systemid/abc"),
            outboundRefs = listOf(OutboundRef(relationName = "person", targetCanonical = fnrHref)),
            malformedHrefs = emptyList(),
        )
        val index = TenantIndex(
            records = listOf(source, target),
            byKey = mapOf(
                fnrHref to target,
                "https://api/administrasjon/personal/personalressurs/systemid/abc" to source,
            ),
        )

        val rows = validator.validate(OrgId("afk_no"), index)

        // Resource was found via PII key → no missing-resource row should appear for this ref.
        assertTrue(
            rows.none { it.problemType == ProblemType.MissingResource },
            "Index lookup must use unmasked canonical keys; got rows: $rows",
        )
    }
}
