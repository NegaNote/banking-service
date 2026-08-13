package com.neganote.bankapi.controller;

import com.neganote.bankapi.dto.account.*;
import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.service.AccountService;
import com.neganote.bankapi.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    private final TransactionService transactionService;

    @GetMapping
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal UserDetails userDetails) {
        return accountService.findMyAccounts(userDetails.getUsername());
    }

    @PostMapping
    public AccountResponse openAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OpenAccountRequest openAccountRequest) {
        return accountService.createAccount(userDetails.getUsername(), openAccountRequest);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccountDetails(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable String accountNumber) {
        return accountService.findMyAccount(accountNumber, userDetails.getUsername());
    }

    @PostMapping("/{accountNumber}/deposits")
    public AccountResponse deposit(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest depositRequest) {
        return accountService.deposit(accountNumber, depositRequest, userDetails.getUsername());
    }

    @PostMapping("/{accountNumber}/withdrawals")
    public AccountResponse withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String accountNumber,
            @Valid @RequestBody WithdrawalRequest withdrawalRequest) {
        return accountService.withdraw(accountNumber, withdrawalRequest, userDetails.getUsername());
    }

    @PostMapping("/{accountNumber}/transfers")
    public AccountResponse transfer(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String accountNumber,
            @Valid @RequestBody TransferRequest transferRequest) {
        return accountService.transfer(accountNumber, transferRequest, userDetails.getUsername());
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactionHistory(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable String accountNumber) {
        return transactionService.findHistoryForAccount(accountNumber, userDetails.getUsername());
    }
}
