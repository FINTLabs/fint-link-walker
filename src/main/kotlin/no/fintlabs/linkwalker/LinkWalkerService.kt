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
class LinkWalkerService(
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
        val entries = fintClient.getEmbeddedResources(task.url, bearer)
        val entriesMap = mutableMapOf(task.url to entries)
        val linkInfos = LinkInfo.fromEntries(entries)

        taskService.addRelations(task, linkInfos)

        linkInfos.map { linkInfo ->
            async {
                entriesMap[linkInfo.url]
                    ?.let { processLinks(task, linkInfo, it) }
                    ?: processLinks(task, linkInfo, fintClient.getEmbeddedResources(linkInfo.url, bearer))
            }
        }.awaitAll()
    }

    private fun processLinks(task: Task, linkInfo: LinkInfo, entries: Collection<JsonNode>) {
        val relationReport = linkParser.parseRelations(linkInfo, entries)
        taskService.addRelationError(task, relationReport.errorCount)
    }

}
