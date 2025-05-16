package no.fintlabs.linkwalker

import com.github.benmanes.caffeine.cache.Cache
import no.fintlabs.linkwalker.model.RelationEntry
import org.springframework.stereotype.Service

@Service
class RelationErrorService(
    private val cache: Cache<String, MutableList<RelationEntry>>
) {

    fun put(taskId: String, errors: List<RelationEntry>) {
        if (errors.isEmpty()) return
        cache.asMap().compute(taskId) { _, existing ->
            (existing ?: mutableListOf()).apply { addAll(errors) }
        }
    }

    fun get(taskId: String): List<RelationEntry> =
        cache.getIfPresent(taskId) ?: emptyList()

}