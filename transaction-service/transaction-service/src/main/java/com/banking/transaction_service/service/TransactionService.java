package com.banking.transaction_service.service;

import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.repository.TransactionRepository;
import event.TransactionCompletedEvent;
import event.TransactionInitiatedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String ,String > redisTemplate;



    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "transaction.fraud";
//    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";


    /**
     * sage step 1 : initiate transfer.
     *  deduct from sender via feign
     *  saves transaction as processing
     *  publish event to kafka for fraud check
     *  return
     *
     * @param request
     * @return
     */
    public TransactionResponse transfer(@Valid TransferRequest request) {
        log.info("saga start transfer from {} to {} and amount {}",request.getSenderAccountNumber(),request.getReceiverAccountNumber(),request.getAmount());
    // deduct from sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(),request.getAmount());
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("transaction saved as processing");

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("Saga step 2 TransactionInitiatedEvent published {}",savedTransaction.getId());
        return mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found {}"+transactionId ))
        );
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
                .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("otp verification for the transaction {} ",transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found"+transactionId));
        String otpKey = "verification:otp"+transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if(storedOtp == null){
            // otp expired
            log.warn("Otp expired for transaction {}"+transactionId);
            compensateTransaction(transaction,"Otp expired and transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }
        if(storedOtp != otp){
            log.warn(" otp is incorrect hence blocking account");
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(
                    transaction,
                    "Wrong OTP entered - transaction cancelled"+
                            "account blocked for security"
            );
            return mapToResponse(transaction);
        }
        log.info("Otp verified completing transaction");
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);



//        return null;
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        TransactionCompletedEvent transactionCompletedEvent= new TransactionCompletedEvent(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),transactionCompletedEvent);
        log.info("Transaction complete transaction No {} ",transaction.getId());


    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        // publish fraud.detected event and account service will block account
        Map<String ,Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId",transaction.getId());
        fraudEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getId(),fraudEvent );
        log.warn("Fraud.detected published account {} will be blocked, kindly connect to your bank",transaction.getSenderAccountNumber());
        // SAGA Compensation and refund transaction
        compensateTransaction(transaction,reason);



    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA compensation refunding {} amount {}",transaction.getSenderAccountNumber(),transaction.getAmount());
        // credit money back to sender synchronously
        accountServiceClient.creditBalance(
                transaction.getSenderAccountNumber(),
                transaction.getAmount()
        );
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason+"SAGA Compensation executed and amount refunded at "+ LocalDateTime.now());
        // publish refund event, notification service will notify user for money movement
        Map<String ,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId",transaction.getId());
        refundEvent.put("senderAccountNumber",transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);
        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);
        log.info("Saga compensation complete and refunded to {}",transaction.getSenderAccountNumber());

    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getFailureReason(),
                transaction.getReferenceNumber(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()-> new RuntimeException("Transaction not found"+transactionId));
        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction not processing, skipping {}",transactionId);
            return;
        }
        compensateTransaction(transaction,transactionId);

    }


}
