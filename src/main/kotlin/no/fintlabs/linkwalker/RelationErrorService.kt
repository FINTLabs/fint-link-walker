package no.fintlabs.linkwalker

import com.github.benmanes.caffeine.cache.AsyncCache
import kotlinx.coroutines.future.await
import no.fintlabs.linkwalker.model.RelationReport
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList

@Service
class RelationErrorService(
    private val cache: AsyncCache<String, CopyOnWriteArrayList<RelationReport>>
) {

    suspend fun add(taskId: String, report: RelationReport) {
        if (report.hasErrors.not()) return
        cache.get(taskId) { CopyOnWriteArrayList() }.await()
            .add(report)
    }

    suspend fun get(taskId: String): List<RelationReport>? =
        cache.getIfPresent(taskId)
            ?.await()
            ?.toList()
}