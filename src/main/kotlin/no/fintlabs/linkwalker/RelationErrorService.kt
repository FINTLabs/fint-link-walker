package no.fintlabs.linkwalker

import com.github.benmanes.caffeine.cache.Cache
import no.fintlabs.linkwalker.model.RelationError
import org.springframework.stereotype.Service

@Service
class RelationErrorService(
    private val cache: Cache<String, MutableList<RelationError>>
) {

    fun put(taskId: String, errors: List<RelationError>) {
        if (errors.isEmpty()) return
        cache.asMap().compute(taskId) { _, existing ->
            (existing ?: mutableListOf()).apply { addAll(errors) }
        }
    }

    fun get(taskId: String): List<RelationError> =
        cache.getIfPresent(taskId) ?: emptyList()

}