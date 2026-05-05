package no.novari.linkwalker.index

import no.novari.fint.model.FintRelation
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

    fun validate(tenant: String, index: TenantIndex): List<ReportRow> {
        val rows = mutableListOf<ReportRow>()
        val resourceCache = mutableMapOf<Pair<String, String>, ResourceInfo?>()

        index.records.forEach { record ->
            val info = resourceCache.getOrPut(record.component to record.resourceName) {
                buildResourceInfo(record.component, record.resourceName)
            }
            validateRecord(tenant, record, info, index, rows)
        }
        return rows
    }

    private fun validateRecord(
        tenant: String,
        record: MinimalRecord,
        info: ResourceInfo?,
        index: TenantIndex,
        rows: MutableList<ReportRow>,
    ) {
        val sourceCanonical: Set<String> = record.canonicalKeys.toSet()

        record.outboundRefs.forEach { ref ->
            val target = index.recordAt(ref.targetCanonical)
            if (target == null) {
                rows += ReportRow(
                    tenant = tenant,
                    component = record.component,
                    resource = record.resourceName,
                    problemType = MISSING_RESOURCE,
                    sourceSelf = sanitizer.safeHref(record, record.displaySelf),
                    targetHref = sanitizer.mask(ref.targetCanonical),
                    relationName = ref.relationName,
                )
                return@forEach
            }
            val relation = info?.relationsByName?.get(ref.relationName.lowercase()) ?: return@forEach
            val inverseName = relation.inverseName ?: return@forEach

            val pointsBack = target.outboundRefs.any { backRef ->
                backRef.relationName.equals(inverseName, ignoreCase = true) &&
                    backRef.targetCanonical in sourceCanonical
            }
            if (!pointsBack) {
                val isAutoRelation = autoRelationRules.isAutoRelation(
                    component = record.component,
                    resourceName = record.resourceName,
                    relationName = ref.relationName,
                )
                rows += ReportRow(
                    tenant = tenant,
                    component = record.component,
                    resource = record.resourceName,
                    problemType = if (isAutoRelation) MISSING_BACK_LINK_AUTORELATION
                                  else MISSING_BACK_LINK_ADAPTER,
                    sourceSelf = sanitizer.safeHref(record, record.displaySelf),
                    targetHref = sanitizer.safeHref(target, target.displaySelf),
                    relationName = ref.relationName,
                    expectedInverseName = inverseName,
                )
            }
        }

        record.malformedHrefs.forEach { badHref ->
            rows += ReportRow(
                tenant = tenant,
                component = record.component,
                resource = record.resourceName,
                problemType = UNKNOWN_LINK,
                sourceSelf = sanitizer.safeHref(record, record.displaySelf),
                targetHref = sanitizer.mask(badHref),
            )
        }
    }

    private fun buildResourceInfo(component: String, resourceName: String): ResourceInfo? {
        val (domain, pkg) = parseComponent(component) ?: return null
        val resource: Resource = metamodelService.getResource(domain, pkg, resourceName) ?: return null
        return ResourceInfo(resource.relations.associateBy { it.name.lowercase() })
    }

    private fun parseComponent(component: String): Pair<String, String>? =
        component.split('_', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }

    private data class ResourceInfo(val relationsByName: Map<String, FintRelation>)

    companion object {
        const val MISSING_RESOURCE = "missing-resource"
        const val UNKNOWN_LINK = "unknown-link"
        const val MISSING_BACK_LINK_AUTORELATION = "missing-back-link-autorelation"
        const val MISSING_BACK_LINK_ADAPTER = "missing-back-link-adapter"
    }
}
