package no.fintlabs.linkwalker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux

class FintClient(
    private val webClient: WebClient
) {

    fun getByteFlow(url: String, bearer: String?): Flow<ByteArray> =
        webClient.get().uri(url)
            .headers { h -> bearer?.let { h.setBearerAuth(it) } }
            .retrieve()
            .bodyToFlux<DataBuffer>()
            .map { db ->
                val arr = ByteArray(db.readableByteCount())
                db.read(arr)
                db.release()
                arr
            }
            .asFlow()

}