package no.fintlabs.linkwalker

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.fintlabs.linkwalker.model.RelationError
import no.fintlabs.linkwalker.task.model.Task
import org.springframework.stereotype.Service

@Service
class LinkwalkerService(
    private val fintClient: FintClient,
    private val linkParser: LinkParserService,
    private val relationErrorService: RelationErrorService
) {

    suspend fun processTask(task: Task, bearer: String) = coroutineScope {
        val selfEntries = fintClient.getEmbeddedResources(task.url, bearer)
        val linkInfos = linkParser.collectLinkInfos(selfEntries).toMutableList()
        val selfLink = linkInfos.first { it.url == task.url }.also(linkInfos::remove)

        selfLink.validateAgainst(selfEntries)
        linkInfos.map { info ->
            async {
                val entries = fintClient.getEmbeddedResources(info.url, bearer)
                info.validateAgainst(entries)
            }
        }.awaitAll()

        val errors = (listOf(selfLink) + linkInfos)
            .filter { it.relationError }
            .map { RelationError(url = it.url) }

        println("relation error count: ${errors.size}")

        relationErrorService.put(task.id, errors)
    }
}
