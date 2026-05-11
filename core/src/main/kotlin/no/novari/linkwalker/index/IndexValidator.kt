package no.novari.linkwalker.index

import no.novari.fint.model.FintRelation
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportRow
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Resource
import org.springframework.stereotype.Component

@Component
class IndexValidator(
    private val metamodelService: MetamodelService,
    private val autoRelationRules: AutoRelationRules,
    private val sanitizer: HrefSanitizer,
) {

    fun validate(orgId: OrgId, index: TenantIndex): List<ReportRow> {
        val rows = mutableListOf<ReportRow>()
        val resourceCache = mutableMapOf<Pair<String, String>, ResourceInfo?>()

        index.records.forEach { record ->
            val info = resourceCache.getOrPut(record.component to record.resourceName) {
                buildResourceInfo(record.component, record.resourceName)
            }
            validateRecord(orgId, record, info, index, rows)
        }
        return rows
    }

    private fun validateRecord(
        orgId: OrgId,
        record: MinimalRecord,
        info: ResourceInfo?,
        index: TenantIndex,
        rows: MutableList<ReportRow>,
    ) {
        val sourceCanonical: Set<String> = record.canonicalKeys.toSet()
        record.outboundRefs.forEach { ref ->
            checkOutboundRef(orgId, record, info, index, sourceCanonical, ref)?.let(rows::add)
        }
        record.malformedHrefs.forEach { badHref ->
            rows += unknownLinkRow(orgId, record, badHref)
        }
    }

    private fun checkOutboundRef(
        orgId: OrgId,
        record: MinimalRecord,
        info: ResourceInfo?,
        index: TenantIndex,
        sourceCanonical: Set<String>,
        ref: OutboundRef,
    ): ReportRow? {
        val target = index.recordAt(ref.targetCanonical)
            ?: return missingResourceRow(orgId, record, ref)

        val inverseName = info?.relationsByName?.get(ref.relationName.lowercase())?.inverseName
            ?: return null

        val pointsBack = target.outboundRefs.any { backRef ->
            backRef.relationName.equals(inverseName, ignoreCase = true) &&
                backRef.targetCanonical in sourceCanonical
        }
        if (pointsBack) return null

        return missingBackLinkRow(orgId, record, target, ref, inverseName)
    }

    private fun missingResourceRow(orgId: OrgId, record: MinimalRecord, ref: OutboundRef) = ReportRow(
        orgId = orgId,
        component = record.component,
        resource = record.resourceName,
        problemType = ProblemType.MissingResource,
        sourceSelf = sanitizer.safeHref(record),
        targetHref = sanitizer.mask(ref.targetCanonical),
        relationName = ref.relationName,
    )

    private fun missingBackLinkRow(
        orgId: OrgId,
        record: MinimalRecord,
        target: MinimalRecord,
        ref: OutboundRef,
        inverseName: String,
    ): ReportRow {
        val isAutoRelation = autoRelationRules.isAutoRelation(
            component = record.component,
            resourceName = record.resourceName,
            relationName = ref.relationName,
        )
        return ReportRow(
            orgId = orgId,
            component = record.component,
            resource = record.resourceName,
            problemType = if (isAutoRelation) ProblemType.MissingBackLinkAutorelation else ProblemType.MissingBackLinkAdapter,
            sourceSelf = sanitizer.safeHref(record),
            targetHref = sanitizer.safeHref(target),
            relationName = ref.relationName,
            expectedInverseName = inverseName,
        )
    }

    private fun unknownLinkRow(orgId: OrgId, record: MinimalRecord, badHref: String) = ReportRow(
        orgId = orgId,
        component = record.component,
        resource = record.resourceName,
        problemType = ProblemType.UnknownLink,
        sourceSelf = sanitizer.safeHref(record),
        targetHref = sanitizer.mask(badHref),
    )

    private fun buildResourceInfo(component: String, resourceName: String): ResourceInfo? {
        val id = ComponentId.parse(component) ?: return null
        val resource: Resource = metamodelService.getResource(id.domain, id.pkg, resourceName) ?: return null
        return ResourceInfo(resource.relations.associateBy { it.name.lowercase() })
    }

    private data class ResourceInfo(val relationsByName: Map<String, FintRelation>)
}
