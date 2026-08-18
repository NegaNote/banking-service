package com.neganote.bankapi.controller.v1;

import com.neganote.bankapi.dto.account.*;
import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.service.AccountService;
import com.neganote.bankapi.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
public class AccountControllerV1 {
    private final AccountService accountService;
    private final TransactionService transactionService;

    @GetMapping
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal Jwt jwt) {
        return accountService.findMyAccounts(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping
    public AccountResponse openAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenAccountRequest openAccountRequest) {
        return accountService.createAccount(Long.parseLong(jwt.getSubject()), openAccountRequest);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccountDetails(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber) {
        return accountService.findMyAccount(accountNumber, Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/{accountNumber}/deposits")
    public AccountResponse deposit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest depositRequest) {
        return accountService.deposit(
                accountNumber, depositRequest, Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/{accountNumber}/withdrawals")
    public AccountResponse withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @Valid @RequestBody WithdrawalRequest withdrawalRequest) {
        return accountService.withdraw(
                accountNumber, withdrawalRequest, Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/{accountNumber}/transfers")
    public AccountResponse transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @Valid @RequestBody TransferRequest transferRequest) {
        return accountService.transfer(
                accountNumber, transferRequest, Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactionHistory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber) {
        return transactionService.findHistoryForAccount(
                accountNumber, Long.parseLong(jwt.getSubject()));
    }
}
