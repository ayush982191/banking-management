package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {
//    private final Fra
    private final AccountServiceClient accountServiceClient;
    public void checkTransaction(Map<String,Object> payload){
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Checking transaction {} account {} amount {} balance {}");
        // perform suspicious activity
        FraudCheckResult result = performFraudCheck(accountNumber,amount,senderBalance);
        if(result.isFraud()){
            log.info("Suspicious activity detected account {} reason {} requesting otp verification ",accountNumber,result.getReason());

        }



    }

    private FraudCheckResult performFraudCheck(String accountNumber, BigDecimal amount, BigDecimal senderBalance) {
        return null;
    }
}
