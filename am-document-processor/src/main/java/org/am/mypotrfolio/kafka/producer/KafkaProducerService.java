package org.am.mypotrfolio.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.am.mypotrfolio.kafka.model.PortfolioUpdateEvent;
import org.am.mypotrfolio.kafka.model.TradeUpdateEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.portfolio-topic}")
    private String portfolioTopic;

    @Value("${app.kafka.trade-topic}")
    private String tradeTopic;


    public void sendMessage(PortfolioUpdateEvent portfolioUpdateEvent) {
        RecordHeaders headers = buildCommonHeaders(
            portfolioUpdateEvent.getId(),
            portfolioUpdateEvent.getUserId(),
            portfolioUpdateEvent.getTimestamp()
        );
        sendKafkaMessage(portfolioTopic, portfolioUpdateEvent.getId().toString(), portfolioUpdateEvent, headers);
    }

    public void sendTradeUpdateEvent(TradeUpdateEvent tradeUpdateEvent) {
        RecordHeaders headers = buildCommonHeaders(
            tradeUpdateEvent.getId(),
            tradeUpdateEvent.getUserId(),
            tradeUpdateEvent.getTimestamp()
        );
        sendKafkaMessage(tradeTopic, tradeUpdateEvent.getId().toString(), tradeUpdateEvent, headers);
    }

    private RecordHeaders buildCommonHeaders(UUID id, String userId, Object timestamp) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("id", id.toString().getBytes());
        headers.add("userId", userId.getBytes());
        headers.add("timestamp", String.valueOf(timestamp).getBytes());
        return headers;
    }

    private void sendKafkaMessage(String topicName, String key, Object event, RecordHeaders headers) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topicName, null, key, event, headers);
        kafkaTemplate.send(record)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Message sent successfully to topic: {}, partition: {}, offset: {}", 
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send message", ex);
                }
            });
    }

}
