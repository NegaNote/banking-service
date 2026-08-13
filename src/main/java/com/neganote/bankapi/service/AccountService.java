package com.neganote.bankapi.service;

import com.neganote.bankapi.dto.account.AccountResponse;
import com.neganote.bankapi.dto.account.DepositRequest;
import com.neganote.bankapi.dto.account.TransferRequest;
import com.neganote.bankapi.dto.account.WithdrawalRequest;
import com.neganote.bankapi.entity.Account;
import com.neganote.bankapi.entity.AccountStatus;
import com.neganote.bankapi.entity.BankTransaction;
import com.neganote.bankapi.entity.TransactionType;
import com.neganote.bankapi.exception.InsufficientFundsException;
import com.neganote.bankapi.exception.InvalidTransferException;
import com.neganote.bankapi.exception.ResourceNotFoundException;
import com.neganote.bankapi.mapper.AccountMapper;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import com.neganote.bankapi.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public static Random RNG_SOURCE = new Random();

    public AccountResponse createAccount(String username) {
        String newAccountNumber = generateAccountNumber();
        while (accountRepository.existsByAccountNumber(newAccountNumber)) {
            newAccountNumber = generateAccountNumber();
        }
        Account newAccount =
                Account.builder()
                        .accountNumber(newAccountNumber)
                        .balance(new BigDecimal("0.00"))
                        .status(AccountStatus.ACTIVE)
                        .owner(
                                userRepository
                                        .findByUsername(username)
                                        .orElseThrow(
                                                () ->
                                                        new ResourceNotFoundException(
                                                                "User not found")))
                        .build();

        accountRepository.save(newAccount);

        return AccountMapper.toAccountResponse(newAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findMyAccounts(String username) {
        return accountRepository.findByOwner_UsernameOrderByCreatedAtDesc(username).stream()
                .map(AccountMapper::toAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findMyAccount(Long id, String username) {
        return AccountMapper.toAccountResponse(
                accountRepository
                        .findByIdAndOwner_Username(id, username)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found")));
    }

    public AccountResponse deposit(Long accountId, DepositRequest request, String username) {
        Account account =
                accountRepository
                        .findByIdAndOwner_Username(accountId, username)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        BankTransaction transaction =
                BankTransaction.builder()
                        .amount(request.getAmount())
                        .type(TransactionType.DEPOSIT)
                        .fromAccount(account)
                        .build();

        transactionRepository.save(transaction);

        account.setBalance(account.getBalance().add(request.getAmount()));

        return AccountMapper.toAccountResponse(accountRepository.save(account));
    }

    public AccountResponse withdraw(Long accountId, WithdrawalRequest request, String username) {
        Account account =
                accountRepository
                        .findByIdAndOwner_Username(accountId, username)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Account has insufficient funds");
        }

        BankTransaction transaction =
                BankTransaction.builder()
                        .amount(request.getAmount())
                        .type(TransactionType.WITHDRAWAL)
                        .fromAccount(account)
                        .build();

        transactionRepository.save(transaction);

        account.setBalance(account.getBalance().subtract(request.getAmount()));

        return AccountMapper.toAccountResponse(accountRepository.save(account));
    }

    public AccountResponse transfer(Long fromAccountId, TransferRequest request, String username) {
        Account sourceAccount =
                accountRepository
                        .findByIdAndOwner_Username(fromAccountId, username)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Source account not found"));

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account not active");
        }

        Account destAccount =
                accountRepository
                        .findByAccountNumber(request.getToAccountNumber())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Destination account not found"));

        if (destAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Destination account not active");
        }

        if (destAccount.getId() == sourceAccount.getId()) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException("Source account has insufficient funds");
        }

        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));

        accountRepository.save(sourceAccount);

        destAccount.setBalance(destAccount.getBalance().add(request.getAmount()));

        accountRepository.save(destAccount);

        BankTransaction transaction =
                BankTransaction.builder()
                        .amount(request.getAmount())
                        .type(TransactionType.TRANSFER)
                        .fromAccount(sourceAccount)
                        .toAccount(destAccount)
                        .description(request.getDescription())
                        .build();

        transactionRepository.save(transaction);

        return AccountMapper.toAccountResponse(sourceAccount);
    }

    private String generateAccountNumber() {
        StringBuilder accountNumber = new StringBuilder();

        String digits = "0123456789";
        for (int i = 0; i < 12; i++) {
            int index = RNG_SOURCE.nextInt(digits.length());
            accountNumber.append(digits.charAt(index));
        }

        return accountNumber.toString();
    }
}
