package com.banking.transaction_service.controller;


import com.banking.transaction_service.dto.TransactionResponse;
import com.banking.transaction_service.dto.TransferRequest;
import com.banking.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transaction")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request
            ){
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.transfer(request));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable String transactionId
    ){
        return ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.getTransaction(transactionId));
    }
    @GetMapping("/{accountNumber}/history")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
            @PathVariable String accountNumber
    ){
        return ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.getTransactionHistory(accountNumber));
    }


    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<List<TransactionResponse>> verifyOtp(
            @PathVariable String transactionId,
            @RequestParam String otp
    ){
        return ResponseEntity.status(HttpStatus.OK)
                .body(transactionService.verifyOtp(transactionId,otp));
    }






}















