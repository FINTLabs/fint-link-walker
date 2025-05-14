package no.fintlabs.linkwalker

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux

@Component
class FintClient(
    private val webclient: WebClient
) {

    fun getByteFlow(url: String, bearer: String?): Flow<ByteArray> =
        webclient.get().uri(url)
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