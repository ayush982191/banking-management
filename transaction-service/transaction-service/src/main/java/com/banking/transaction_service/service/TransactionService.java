package com.banking.transaction_service.service;

import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.event.TransactionInitiatedEvent;
import com.banking.transaction_service.repository.TransactionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.Uuid;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String  TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String  TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String  TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    /**
     *
     * Step 1 initiate transfer
     * deduct from sender via feign
     * saves transaction as processing
     * returns
     */
    public TransactionResponse transfer(@Valid TransferRequest request) {
//        return null;
        log.info("Saga start ");
        accountServiceClient.deductBalance(
                request.getSenderAccountNumber(),
                request.getAmount()
        );
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("transaction saved as processing {}",savedTransaction.getId());
        log.info("saga step 2, publish for fraud check");
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,savedTransaction.getId(),event);
        log.info("Safa step 2, transaction initiated event published");
        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction savedTransaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(savedTransaction.getId());
        response.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        response.setAmount(savedTransaction.getAmount());
        response.setTransactionType(savedTransaction.getTransactionType());
        response.setTransactionStatus(savedTransaction.getTransactionStatus());
        response.setDescription(savedTransaction.getDescription());
        response.setFailureReason(savedTransaction.getFailureReason());
        response.setReferenceNumber(savedTransaction.getReferenceNumber());
        response.setCreatedAt(savedTransaction.getCreatedAt());
        response.setCompletedAt(savedTransaction.getCompletedAt());
        return response;
    }


    public TransactionResponse getTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(()->new RuntimeException("Transaction Not Found "+transactionId));

//        return null;
        return mapToResponse(transaction);
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        List<Transaction> response = transactionRepository.findBySenderAccountNumberCreatedAtDesc(accountNumber)
                .orElseThrow(()->new RuntimeException("No Transaction found"));
        return response
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    public List<TransactionResponse> verifyOtp(String transactionId, String otp) {
        return null;
    }
}
