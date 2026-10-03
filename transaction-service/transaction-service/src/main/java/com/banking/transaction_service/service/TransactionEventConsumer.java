package com.banking.transaction_service.service;

import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private static final SecureRandom secureRandom = new SecureRandom();
    private final RedisTemplate<String ,String > redisTemplate;
    private static final Long otpExpiryMinutes = 8L;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";
    /**
     * consume verification.required topic
     * generate otp and ask user to verify
     * @param payload
     */
    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(
            @Payload Map<String ,Object> payload
            ){
        try{
            String transactionId = (String) payload.get("transactionId");
            String accountNumber = (String) payload.get("accountNumber");
            String reason = (String)payload.get("reason");
            String amount = (String)payload.get("amount");
            log.info("verification required transaction {} and reason {}",transactionId,reason);
            Transaction transaction =transactionRepository.findById(transactionId)
                    .orElseThrow(()-> new RuntimeException("Transaction not found "+transactionId));
            if(transaction.getStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction not processing, skipping {}",transactionId);
                return;
            }
            // generate 6 digit otp
            String  otp = String.format("%06d", secureRandom.nextInt(1_000_000));
            // store otp in redis
            String otpKey = "verification:otp"+transactionId;
            redisTemplate.opsForValue().set(otpKey,otp,otpExpiryMinutes, TimeUnit.MINUTES);
            // update status to pending verification
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);
            log.info("Otp generated for transaction {} and expires in {} minutes",transactionId,otpExpiryMinutes);
            // notify user i.e publish notification event.
            Map<String ,Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transaction);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",otp);
            otpEvent.put("amount",amount);
            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);

        } catch (Exception e) {
//            throw new RuntimeException(e);
            log.error("error handling verification required "+ e.getMessage());
        }



    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCleanResult(
            @Payload Map<String ,Object> payload
    ){
        try{
            String transactionId = (String)payload.get("transactionId");
            transactionService.processCleanResult(transactionId);

        } catch (Exception e) {
            log.error("Error processing fraud check result : {}",e.getMessage());
//            throw new RuntimeException(e);
        }

    }




}
