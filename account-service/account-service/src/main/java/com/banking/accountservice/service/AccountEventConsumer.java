package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {
    private final AccountService accountService;
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
            @Payload Map<String,Object> payload
            ){
        try {
            String receiverAccount = (String)payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            log.info("Crediting account: {} and amount: {}",receiverAccount,amount);
            accountService.creditBalance(receiverAccount,amount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(
            @Payload Map<String,Object> payload
    ){
        try {
            // comments on fraud detected event using kafka
            String accountNumber = (String)payload.get("accountNumber");
            log.info("Fraud detected and I am blocking account number");
            accountService.blockAccount(accountNumber);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }



}
