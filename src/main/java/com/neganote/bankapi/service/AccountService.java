package com.neganote.bankapi.service;

import com.neganote.bankapi.dto.account.*;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    public static Random RNG_SOURCE = new Random();

    public AccountResponse createAccount(Long userId, OpenAccountRequest openAccountRequest) {
        logger.info("Creating account for userId={}", userId);
        String newAccountNumber = generateAccountNumber();
        while (accountRepository.existsByAccountNumber(newAccountNumber)) {
            newAccountNumber = generateAccountNumber();
        }
        Account newAccount =
                Account.builder()
                        .accountNumber(newAccountNumber)
                        .balance(new BigDecimal("0.00"))
                        .status(AccountStatus.ACTIVE)
                        .ownerId(userId)
                        .build();

        accountRepository.save(newAccount);
        logger.info(
                "Account created with accountNumber={} for userId={}", newAccountNumber, userId);
        return AccountMapper.toAccountResponse(newAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findMyAccounts(Long userId) {
        return accountRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(AccountMapper::toAccountResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findMyAccount(String accountNumber, Long userId) {
        return AccountMapper.toAccountResponse(
                accountRepository
                        .findByAccountNumberAndOwnerId(accountNumber, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found")));
    }

    public AccountResponse deposit(String accountNumber, DepositRequest request, Long userId) {
        logger.info(
                "Attempting to deposit {} into accountNumber={} for userId={}",
                request.getAmount(),
                accountNumber,
                userId);
        Account account =
                accountRepository
                        .findByAccountNumberAndOwnerId(accountNumber, userId)
                        .orElseThrow(
                                () -> {
                                    logger.error(
                                            "Account not found for accountNumber={} and userId={}",
                                            accountNumber,
                                            userId);
                                    return new ResourceNotFoundException("Account not found");
                                });

        BankTransaction transaction =
                BankTransaction.builder()
                        .amount(request.getAmount())
                        .type(TransactionType.DEPOSIT)
                        .fromAccount(account)
                        .build();

        transactionRepository.save(transaction);

        account.setBalance(account.getBalance().add(request.getAmount()));

        logger.info(
                "Deposit successful. New balance for accountNumber={} is {}",
                accountNumber,
                account.getBalance());

        return AccountMapper.toAccountResponse(accountRepository.save(account));
    }

    public AccountResponse withdraw(String accountNumber, WithdrawalRequest request, Long userId) {
        logger.info(
                "Attempting to withdraw {} from accountNumber={} for userId={}",
                request.getAmount(),
                accountNumber,
                userId);
        Account account =
                accountRepository
                        .findByAccountNumberAndOwnerId(accountNumber, userId)
                        .orElseThrow(
                                () -> {
                                    logger.error(
                                            "Account not found for accountNumber={} and userId={}",
                                            accountNumber,
                                            userId);
                                    return new ResourceNotFoundException("Account not found");
                                });

        if (account.getStatus() != AccountStatus.ACTIVE) {
            logger.warn(
                    "Account not active for accountNumber={} and userId={}", accountNumber, userId);
            throw new IllegalStateException("Account is not active");
        }

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            logger.warn(
                    "Insufficient funds for accountNumber={} and userId={}", accountNumber, userId);
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

        logger.info(
                "Withdrawal successful. New balance for accountNumber={} is {}",
                accountNumber,
                account.getBalance());

        return AccountMapper.toAccountResponse(accountRepository.save(account));
    }

    public AccountResponse transfer(
            String fromAccountNumber, TransferRequest request, Long userId) {
        logger.info(
                "Attempting to transfer {} from accountNumber={} to accountNumber={} for userId={}",
                request.getAmount(),
                fromAccountNumber,
                request.getToAccountNumber(),
                userId);
        Account sourceAccount =
                accountRepository
                        .findByAccountNumberAndOwnerId(fromAccountNumber, userId)
                        .orElseThrow(
                                () -> {
                                    logger.error(
                                            "Source account not found for accountNumber={} and"
                                                    + " userId={}",
                                            fromAccountNumber,
                                            userId);
                                    return new ResourceNotFoundException(
                                            "Source account not found");
                                });

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            logger.warn(
                    "Source account not active for accountNumber={} and userId={}",
                    fromAccountNumber,
                    userId);
            throw new IllegalStateException("Source account not active");
        }

        Account destAccount =
                accountRepository
                        .findByAccountNumber(request.getToAccountNumber())
                        .orElseThrow(
                                () -> {
                                    logger.error(
                                            "Destination account not found for accountNumber={}",
                                            request.getToAccountNumber());
                                    return new ResourceNotFoundException(
                                            "Destination account not found");
                                });

        if (destAccount.getStatus() != AccountStatus.ACTIVE) {
            logger.warn(
                    "Destination account not active for accountNumber={}",
                    request.getToAccountNumber());
            throw new IllegalStateException("Destination account not active");
        }

        if (destAccount.getId().equals(sourceAccount.getId())) {
            logger.warn(
                    "Cannot transfer to the same account for accountNumber={}",
                    request.getToAccountNumber());
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            logger.warn(
                    "Source account has insufficient funds for accountNumber={}",
                    fromAccountNumber);
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

        logger.info(
                "Transfer successful. New balance for sourceAccountNumber={} is {}, new balance for"
                        + " destinationAccountNumber={} is {}",
                fromAccountNumber,
                sourceAccount.getBalance(),
                request.getToAccountNumber(),
                destAccount.getBalance());

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
