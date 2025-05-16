package no.fintlabs.linkwalker

import com.github.benmanes.caffeine.cache.Cache
import no.fintlabs.linkwalker.model.RelationReport
import org.springframework.stereotype.Service

@Service
class RelationErrorService(
    private val cache: Cache<String, MutableList<RelationReport>>
) {

    fun put(taskId: String, errors: List<RelationReport>) {
        if (errors.isEmpty()) return
        cache.asMap().compute(taskId) { _, existing ->
            (existing ?: mutableListOf()).apply { addAll(errors) }
        }
    }

    fun get(taskId: String): List<RelationReport> =
        cache.getIfPresent(taskId) ?: emptyList()

}