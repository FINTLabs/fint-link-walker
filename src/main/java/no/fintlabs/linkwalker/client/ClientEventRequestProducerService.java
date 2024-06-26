package no.fintlabs.linkwalker.client;


import lombok.extern.slf4j.Slf4j;
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.RequestProducer;
import no.fintlabs.kafka.requestreply.RequestProducerConfiguration;
import no.fintlabs.kafka.requestreply.RequestProducerFactory;
import no.fintlabs.kafka.requestreply.RequestProducerRecord;
import no.fintlabs.kafka.requestreply.topic.ReplyTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.ReplyTopicService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class ClientEventRequestProducerService {

    private final RequestTopicNameParameters requestTopicNameParameters;
    private final RequestProducer<ClientEvent, ClientEvent> requestProducer;

    public ClientEventRequestProducerService(
            @Value("${fint.kafka.application-id}") String applicationId,
            RequestProducerFactory requestProducerFactory,
            ReplyTopicService replyTopicService
    ) {
        ReplyTopicNameParameters replyTopicNameParameters = ReplyTopicNameParameters.builder()
                .applicationId(applicationId)
                .domainContext("fint-customer-objects")
                .resource("client")
                .build();

        replyTopicService.ensureTopic(replyTopicNameParameters, 0, TopicCleanupPolicyParameters.builder().build());

        this.requestTopicNameParameters = RequestTopicNameParameters.builder()
                .domainContext("fint-customer-objects")
                .resource("client")
                .build();

        this.requestProducer = requestProducerFactory.createProducer(
                replyTopicNameParameters,
                ClientEvent.class,
                ClientEvent.class,
                RequestProducerConfiguration
                        .builder()
                        .defaultReplyTimeout(Duration.ofMinutes(5))
                        .build()
        );
    }

    public Optional<ClientEvent> get(ClientEvent clientEvent) {
        log.info("Sending request to get client: {}", clientEvent);
        return requestProducer.requestAndReceive(
                        RequestProducerRecord.<ClientEvent>builder()
                                .topicNameParameters(requestTopicNameParameters)
                                .value(clientEvent)
                                .build()
                )
                .map(ConsumerRecord::value);
    }
}