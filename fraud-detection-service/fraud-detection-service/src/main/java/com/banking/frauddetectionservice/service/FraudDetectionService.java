package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {
    private final AccountServiceClient accountServiceClient;
    private static final String VERIFICATION_REQUIRE_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";
    private final KafkaTemplate<String ,Object> kafkaTemplate;
    private final RedisTemplate<String ,String > redisTemplate;
    private int maxTransactionPerMinute = 6;
    private double suspiciousAmountMultiplier = 5;


    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        // fetch real balance from account service.
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Checking transaction {} account {} amount {} balance {}  ",transactionId,accountNumber,amount,senderBalance );
        FraudCheckResult result = performFraudChecks(accountNumber,amount,senderBalance);
        if(result.isFraud()){
            log.info("Suspicious activity detected in account {} and requesting otp verification",accountNumber);
            Map<String,Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId",transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount",amount);
            verificationEvent.put("reason",result.getReason());
            kafkaTemplate.send(VERIFICATION_REQUIRE_TOPIC,transactionId,verificationEvent);


        }else {
            // transaction is clean
            log.info("Transaction is clean");
            Map<String ,Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId",transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason",null );
            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,transactionCleanEvent);


        }


    }

    private FraudCheckResult performFraudChecks(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {
        if (isBalanceCheckFailed(senderBalance, amount)) {
            log.warn("Fraud check failed: Insufficient balance for account {}", accountNumber);
            return new FraudCheckResult(true, "Insufficient sender balance");
        }

        if (isVelocityExceeded(accountNumber)) {
            log.warn("Fraud check failed: Velocity limit exceeded for account {}", accountNumber);
            return new FraudCheckResult(true, "Transaction velocity limit exceeded");
        }

        if (isAmountSuspicious(accountNumber, amount)) {
            log.warn("Fraud check failed: Suspicious transaction amount for account {}", accountNumber);
            return new FraudCheckResult(true, "Suspicious transaction amount detected");
        }

        return new FraudCheckResult(false, null);
    }


    private boolean isVelocityExceeded(String accountNumber){
        String key = "fraud:velocity:" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);
        if(count != null  && count==1){
            redisTemplate.expire(key,60, TimeUnit.SECONDS);
        }
        log.info("velocity check account {} count {}",accountNumber,count);
        return count != null && count > maxTransactionPerMinute;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount){
        if (accountNumber == null || amount == null) {
            return false;
        }

        String key = "fraud:avg_amount:" + accountNumber;
        String avgAmountStr = redisTemplate.opsForValue().get(key);

        if (avgAmountStr != null) {
            try {
                BigDecimal avgAmount = new BigDecimal(avgAmountStr);
                BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

                log.info("Account {} average amount: {}, threshold: {}, current amount: {}",
                        accountNumber, avgAmount, threshold, amount);

                if (amount.compareTo(threshold) > 0) {
                    log.warn("Suspicious amount detected for account {}: {} exceeds threshold {}",
                            accountNumber, amount, threshold);
                    return true;
                }

                // Update running average for the account
                BigDecimal updatedAvg = avgAmount.add(amount).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                redisTemplate.opsForValue().set(key, updatedAvg.toString());
            } catch (NumberFormatException e) {
                log.error("Error parsing average amount from Redis for account {}", accountNumber, e);
                redisTemplate.opsForValue().set(key, amount.toString());
            }
        } else {
            // First recorded transaction: store as initial baseline average
            redisTemplate.opsForValue().set(key, amount.toString());
        }

        return false;
    }

    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount){
        if (senderBalance == null || amount == null) {
            log.warn("Balance check failed: senderBalance or amount is null");
            return true;
        }

        return amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(senderBalance) > 0;
    }

}
