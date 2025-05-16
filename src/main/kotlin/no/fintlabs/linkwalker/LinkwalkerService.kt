package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.fintlabs.linkwalker.model.LinkInfo
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
        val linkInfos: MutableList<LinkInfo> = linkParser.collectLinkInfos(selfEntries).toMutableList()

        validateSelfLinks(linkInfos, task.url, selfEntries)
        linkInfos.map { info ->
            async {
                val entries = fintClient.getEmbeddedResources(info.url, bearer)
                info.validateAgainst(entries)
            }
        }.awaitAll()

        val errors = linkInfos.filter { it.relationError }
            .flatMap { linkInfo -> linkInfo.ids.flatMap { it.value } }

        println("Errors: ${errors.size}")

//        relationErrorService.put(task.id, errors)
    }


    private fun validateSelfLinks(linkInfos: MutableList<LinkInfo>, selfUrl: String, selfEntries: List<JsonNode>) =
        linkInfos.first { it.url == selfUrl }.let { selfLinkInfo ->
            selfLinkInfo.validateAgainst(selfEntries)
            linkInfos.remove(selfLinkInfo)
        }

}
