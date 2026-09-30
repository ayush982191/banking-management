package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.repository.AccountRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /**
     * Creates a new bank account with initial deposit and active status
     */
    @Transactional
    public AccountResponse createAccount(@Valid CreateAccountRequest request) {
        log.info("Creating account for email: {}", request.getEmail());

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setDailyTransactionLimit(new BigDecimal("50000.00")); // Default limit

        Account savedAccount = accountRepository.save(account);
        log.info("Account created successfully with account number: {}", savedAccount.getAccountNumber());
        return mapToAccountResponse(savedAccount);
    }

    /**
     * Retrieves account details by account number
     */
    public AccountResponse getAccount(String accountNumber) {
        log.info("Fetching account details for account number: {}", accountNumber);
        Account account = findAccountByNumber(accountNumber);
        return mapToAccountResponse(account);
    }

    /**
     * Retrieves current balance of an account
     */
    public BigDecimal getBalance(String accountNumber) {
        log.info("Fetching balance for account number: {}", accountNumber);
        Account account = findAccountByNumber(accountNumber);
        return account.getBalance();
    }

    /**
     * Blocks an account by account number
     */
    @Transactional
    public void blockAccount(String accountNumber) {
        log.info("Blocking account number: {}", accountNumber);
        Account account = findAccountByNumber(accountNumber);
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account number {} has been blocked", accountNumber);
    }

    /**
     * Deducts (debits) balance from the account
     * called by transaction service
     *
     */
    @Transactional
    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting {} from sender account number: {}", amount, accountNumber);
        Account account = findAccountByNumber(accountNumber);

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not ACTIVE. Current status: " + account.getAccountStatus());
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in account: " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Deducted {} successfully. New balance: {}", amount, account.getBalance());
    }

    /**
     * Credits balance to the account
     * called by transaction service via kafka
     *
     */
    @Transactional
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account number: {}", amount, accountNumber);
        Account account = findAccountByNumber(accountNumber);
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not ACTIVE. Current status: " + account.getAccountStatus());
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Credited {} successfully. New balance: {}", amount, account.getBalance());
    }


    // --- Helper Methods ---

    private Account findAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with account number: " + accountNumber));
    }

    private String generateAccountNumber() {
        // Generates a 12-digit random account number
        Random random = new Random();
        long number = 100000000000L + (long)(random.nextDouble() * 900000000000L);
        return String.valueOf(number);
    }

    private AccountResponse mapToAccountResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setAccountStatus(account.getAccountStatus());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setInitialDeposit(account.getBalance());
        response.setCreatedAt(account.getCreatedAt());
        response.setUpdateAt(account.getUpdatedAt());
        return response;
    }
}
