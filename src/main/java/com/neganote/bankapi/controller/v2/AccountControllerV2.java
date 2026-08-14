package com.neganote.bankapi.controller.v2;

import com.neganote.bankapi.dto.account.*;
import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.entity.IdempotencyRecord;
import com.neganote.bankapi.entity.User;
import com.neganote.bankapi.service.AccountService;
import com.neganote.bankapi.service.IdempotencyService;
import com.neganote.bankapi.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/accounts")
public class AccountControllerV2 {
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;

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
    public ResponseEntity<AccountResponse> deposit(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest depositRequest,
            HttpServletRequest httpRequest) {
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, user, httpRequest.getRequestURI(), depositRequest);

        if (existing.isPresent()) {
            // Replay the cached response, same status, same body
            IdempotencyRecord idempotencyRecord = existing.get();
            AccountResponse cached =
                    jsonMapper.readValue(
                            idempotencyRecord.getResponseBody(), AccountResponse.class);
            return ResponseEntity.status(idempotencyRecord.getResponseStatus()).body(cached);
        }

        // 2. Perform the real operation
        AccountResponse response =
                accountService.deposit(accountNumber, depositRequest, user.getUsername());

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey, user, httpRequest.getRequestURI(), depositRequest, 200, response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountNumber}/withdrawals")
    public ResponseEntity<AccountResponse> withdraw(
            @AuthenticationPrincipal User user,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest withdrawalRequest,
            HttpServletRequest httpRequest) {
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, user, httpRequest.getRequestURI(), withdrawalRequest);

        if (existing.isPresent()) {
            // Replay the cached response, same status, same body
            IdempotencyRecord idempotencyRecord = existing.get();
            AccountResponse cached =
                    jsonMapper.readValue(
                            idempotencyRecord.getResponseBody(), AccountResponse.class);
            return ResponseEntity.status(idempotencyRecord.getResponseStatus()).body(cached);
        }

        // 2. Perform the real operation
        AccountResponse response =
                accountService.withdraw(accountNumber, withdrawalRequest, user.getUsername());

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey,
                user,
                httpRequest.getRequestURI(),
                withdrawalRequest,
                200,
                response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountNumber}/transfers")
    public ResponseEntity<AccountResponse> transfer(
            @AuthenticationPrincipal User user,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest transferRequest,
            HttpServletRequest httpRequest) {
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, user, httpRequest.getRequestURI(), transferRequest);

        if (existing.isPresent()) {
            // Replay the cached response, same status, same body
            IdempotencyRecord idempotencyRecord = existing.get();
            AccountResponse cached =
                    jsonMapper.readValue(
                            idempotencyRecord.getResponseBody(), AccountResponse.class);
            return ResponseEntity.status(idempotencyRecord.getResponseStatus()).body(cached);
        }

        // 2. Perform the real operation
        AccountResponse response =
                accountService.transfer(accountNumber, transferRequest, user.getUsername());

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey, user, httpRequest.getRequestURI(), transferRequest, 200, response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactionHistory(
            @AuthenticationPrincipal UserDetails userDetails, @PathVariable String accountNumber) {
        return transactionService.findHistoryForAccount(accountNumber, userDetails.getUsername());
    }
}
