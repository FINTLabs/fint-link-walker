package no.fintlabs.linkwalker

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import no.fintlabs.linkwalker.model.LinkInfo
import no.fintlabs.linkwalker.model.Status
import no.fintlabs.linkwalker.task.TaskService
import no.fintlabs.linkwalker.task.model.Task
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class LinkwalkerService(
    applicationScope: CoroutineScope,
    private val fintClient: FintClient,
    private val taskService: TaskService,
    private val linkParser: LinkParserService,
    @Qualifier("taskProcessorDispatcher")
    private val taskDispatcher: CoroutineDispatcher,
    private val taskChannel: Channel<Pair<Task, String>>,
    private val relationErrorService: RelationErrorService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        applicationScope.launch(taskDispatcher) {
            for ((task, bearer) in taskChannel) {
                try {
                    task.status = Status.PROCESSING
                    processTask(task, bearer)
                    task.status = Status.FINISHED
                } catch (ex: Throwable) {
                    task.status = Status.FAILED
                    logger.error("Task: ${task.id} failed due to: ${ex.message}")
                }
            }
        }
    }

    suspend fun processTask(task: Task, bearer: String) = coroutineScope {
        val selfEntries = fintClient.getEmbeddedResources(task.url, bearer)
        val linkInfos: MutableList<LinkInfo> = linkParser.collectLinkInfos(selfEntries).toMutableList()
        taskService.addRelations(task, linkInfos)

        validateSelfLinks(task, linkInfos, selfEntries)
        linkInfos.map { info ->
            async {
                val entries = fintClient.getEmbeddedResources(info.url, bearer)
                info.validateAgainst(entries)
            }
        }.awaitAll()

        val errors = linkInfos.filter { it.relationError }
            .flatMap { linkInfo -> linkInfo.ids.flatMap { it.value } }

        println("Errors: ${errors.size}")
    }


    private fun validateSelfLinks(
        task: Task,
        linkInfos: MutableList<LinkInfo>,
        selfEntries: List<JsonNode>
    ) =
        linkInfos.first { it.url == task.url }.let { selfLinkInfo ->
            selfLinkInfo.validateAgainst(selfEntries)
            taskService.addRelationError(task, selfLinkInfo)
            linkInfos.remove(selfLinkInfo)
        }

}
