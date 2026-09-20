package com.banking.accountservice.service;
import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.repository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;

    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = findAccount(accountNumber);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }


    public void blockAccount(String accountNumber) {
        Account account = findAccount(accountNumber);
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
    }


    private Account findAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }

    public BigDecimal getBalance(String accountNumber) {
        return findAccount(accountNumber).getBalance();
    }

    public AccountResponse getAccount(String accountNumber) {
        return mapToResponse(findAccount(accountNumber));
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        Account account = findAccount(accountNumber);
        log.info("Crediting to account "+accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    public AccountResponse createAccount(@Valid CreateAccountRequest request) {
        log.info("Creating account for {} ");
        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account already exist"+request.getEmail());
        }
        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber("ACC" + System.currentTimeMillis());
        account.setDailyTransactionLimit(BigDecimal.valueOf(10000));
        Account savedAccount = accountRepository.save(account);
        return mapToResponse(savedAccount);
    }

    private AccountResponse mapToResponse(Account savedAccount) {
        AccountResponse response = new AccountResponse();
        response.setId(savedAccount.getId());
        response.setAccountNumber(savedAccount.getAccountNumber());
        response.setAccountHolderName(savedAccount.getAccountHolderName());
        response.setEmail(savedAccount.getEmail());
        response.setPhone(savedAccount.getPhone());
        response.setAccountType(savedAccount.getAccountType());
        response.setAccountStatus(savedAccount.getAccountStatus());
        response.setBalance(savedAccount.getBalance());
        response.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        response.setCreated_at(savedAccount.getCreated_at());
        return response;
    }

}