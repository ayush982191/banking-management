package com.banking.transaction_service.service;

import com.banking.transaction_service.client.AccountServiceClient;
import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.entity.Transaction;
import com.banking.transaction_service.entity.TransactionStatus;
import com.banking.transaction_service.entity.TransactionType;
import com.banking.transaction_service.repository.TransactionRepository;
import event.TransactionInitiatedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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



    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
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
        return mapToResponse();
    }

    public TransactionResponse getTransaction(String transactionId) {
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
    }
}
