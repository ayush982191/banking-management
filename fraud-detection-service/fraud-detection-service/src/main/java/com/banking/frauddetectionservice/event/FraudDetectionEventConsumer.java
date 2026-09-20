package com.banking.frauddetectionservice.event;
import com.banking.frauddetectionservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionEventConsumer {
    private final FraudDetectionService fraudDetectionService;
    /*
    Listent to transaction.initiated topic.
    every transaction goes through fraud check b4 completing.
     */
    @KafkaListener(topics = "transaction.initiated", groupId="fraud-detection-group")
    public void consumeTransactionInitiated(
            @Payload Map<String,Object> payload
            ){
        log.info("received transaction for fraud check {) ",payload.get("transactionId"));

        try {

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
