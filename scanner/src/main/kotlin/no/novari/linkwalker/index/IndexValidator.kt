package no.novari.linkwalker.index

import no.novari.fint.model.FintRelation
import no.novari.linkwalker.OrgId
import no.novari.linkwalker.report.ProblemType
import no.novari.linkwalker.report.ReportProblem
import no.novari.metamodel.MetamodelService
import no.novari.metamodel.model.Resource
import org.springframework.stereotype.Component

@Component
class IndexValidator(
    private val metamodelService: MetamodelService,
    private val autoRelationRules: AutoRelationRules,
    private val sanitizer: HrefSanitizer,
) {

    fun validate(orgId: OrgId, index: TenantIndex): List<ReportProblem> {
        val problems = mutableListOf<ReportProblem>()
        val resourceCache = mutableMapOf<Pair<String, String>, ResourceInfo?>()

        index.records.forEach { record ->
            val info = resourceCache.getOrPut(record.component to record.resourceName) {
                buildResourceInfo(record.component, record.resourceName)
            }
            validateRecord(orgId, record, info, index, problems)
        }
        return problems
    }

    private fun validateRecord(
        orgId: OrgId,
        record: MinimalRecord,
        info: ResourceInfo?,
        index: TenantIndex,
        problems: MutableList<ReportProblem>,
    ) {
        val sourceCanonical: Set<String> = record.canonicalKeys.toSet()
        record.outboundRefs.forEach { ref ->
            checkOutboundRef(orgId, record, info, index, sourceCanonical, ref)?.let(problems::add)
        }
        record.malformedHrefs.forEach { badHref ->
            problems += unknownLinkProblem(orgId, record, badHref)
        }
    }

    private fun checkOutboundRef(
        orgId: OrgId,
        record: MinimalRecord,
        info: ResourceInfo?,
        index: TenantIndex,
        sourceCanonical: Set<String>,
        ref: OutboundRef,
    ): ReportProblem? {
        val target = index.recordAt(ref.targetCanonical)
            ?: return missingResourceProblem(orgId, record, ref)

        val inverseName = info?.relationsByName?.get(ref.relationName.lowercase())?.inverseName
            ?: return null

        val pointsBack = target.outboundRefs.any { backRef ->
            backRef.relationName.equals(inverseName, ignoreCase = true) &&
                backRef.targetCanonical in sourceCanonical
        }
        if (pointsBack) return null

        return missingBackLinkProblem(orgId, record, target, ref, inverseName)
    }

    private fun missingResourceProblem(orgId: OrgId, record: MinimalRecord, ref: OutboundRef) = ReportProblem(
        orgId = orgId,
        component = record.component,
        resource = record.resourceName,
        problemType = ProblemType.MissingResource,
        sourceSelf = sanitizer.safeHref(record),
        targetHref = sanitizer.mask(ref.targetCanonical),
        relationName = ref.relationName,
    )

    private fun missingBackLinkProblem(
        orgId: OrgId,
        record: MinimalRecord,
        target: MinimalRecord,
        ref: OutboundRef,
        inverseName: String,
    ): ReportProblem {
        val isAutoRelation = autoRelationRules.isAutoRelation(
            component = record.component,
            resourceName = record.resourceName,
            relationName = ref.relationName,
        )
        return ReportProblem(
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

    private fun unknownLinkProblem(orgId: OrgId, record: MinimalRecord, badHref: String) = ReportProblem(
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
