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

        cache
            .get(taskId) { _: String -> CopyOnWriteArrayList<RelationReport>() }  // 1-param λ ⇒ Function<K,V>
            .await()
            .add(report)
    }


    suspend fun get(taskId: String): List<RelationReport> =
        cache.get(taskId) { _: String -> CopyOnWriteArrayList<RelationReport>() }
            .await()
            .toList()
}