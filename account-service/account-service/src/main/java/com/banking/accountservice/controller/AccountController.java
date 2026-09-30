package com.banking.accountservice.controller;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * 1. Create a new account
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Received request to create account for holder: {}", request.getAccountHolderName());
        AccountResponse response = accountService.createAccount(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * 2. Get account details by account number
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable String accountNumber) {
        log.info("Received request to fetch account details for account: {}", accountNumber);
        AccountResponse response = accountService.getAccount(accountNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Get account balance by account number
     */
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountNumber) {
        log.info("Received request to fetch balance for account: {}", accountNumber);
        BigDecimal balance = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(balance);
    }

    /**
     * 4. Block an account by account number
     */
    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(@PathVariable String accountNumber) {
        log.info("Received request to block account: {}", accountNumber);
          accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked successfully");
    }

    /**
     * Saga step 1. Deduct Balance
     * called by transaction service when transaction is initiated
     */

    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount) {

        // your logic here (call service, etc.)
        accountService.deductBalance(accountNumber,amount);

        return ResponseEntity.ok("Amount Deducted successfully");
    }

    /**
     * Saga step 4. compensating transaction endpoint.
     *  called by transaction service in 2 scenario
     *  1. Fraud detected. refund sender.
     *  2. transaction completed : credit receiver
     *
     */
    public ResponseEntity<String> creditBalance(
            @PathVariable String accountNumber,
            @RequestParam BigDecimal amount
    ){
        accountService.creditBalance(accountNumber,amount);
        return ResponseEntity.ok("Balance Credited Successfully");
    }



}
