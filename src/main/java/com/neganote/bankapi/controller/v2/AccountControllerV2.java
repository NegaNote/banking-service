package com.neganote.bankapi.controller.v2;

import com.neganote.bankapi.dto.account.*;
import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.idempotency.IdempotencyRecord;
import com.neganote.bankapi.idempotency.IdempotencyService;
import com.neganote.bankapi.service.AccountService;
import com.neganote.bankapi.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public List<AccountResponse> getAccounts(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return accountService.findMyAccounts(userId);
    }

    @PostMapping
    public AccountResponse openAccount(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenAccountRequest openAccountRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        return accountService.createAccount(userId, openAccountRequest);
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse getAccountDetails(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber) {
        Long userId = Long.parseLong(jwt.getSubject());
        return accountService.findMyAccount(accountNumber, userId);
    }

    @PostMapping("/{accountNumber}/deposits")
    public ResponseEntity<AccountResponse> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String accountNumber,
            @Valid @RequestBody DepositRequest depositRequest,
            HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, userId, httpRequest.getRequestURI(), depositRequest);

        if (existing.isPresent()) {
            // Replay the cached response, same status, same body
            IdempotencyRecord idempotencyRecord = existing.get();
            AccountResponse cached =
                    jsonMapper.readValue(
                            idempotencyRecord.getResponseBody(), AccountResponse.class);
            return ResponseEntity.status(idempotencyRecord.getResponseStatus()).body(cached);
        }

        // 2. Perform the real operation
        AccountResponse response = accountService.deposit(accountNumber, depositRequest, userId);

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey, userId, httpRequest.getRequestURI(), depositRequest, 200, response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountNumber}/withdrawals")
    public ResponseEntity<AccountResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest withdrawalRequest,
            HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, userId, httpRequest.getRequestURI(), withdrawalRequest);

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
                accountService.withdraw(accountNumber, withdrawalRequest, userId);

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey,
                userId,
                httpRequest.getRequestURI(),
                withdrawalRequest,
                200,
                response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountNumber}/transfers")
    public ResponseEntity<AccountResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest transferRequest,
            HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        Optional<IdempotencyRecord> existing =
                idempotencyService.findExisting(
                        idempotencyKey, userId, httpRequest.getRequestURI(), transferRequest);

        if (existing.isPresent()) {
            // Replay the cached response, same status, same body
            IdempotencyRecord idempotencyRecord = existing.get();
            AccountResponse cached =
                    jsonMapper.readValue(
                            idempotencyRecord.getResponseBody(), AccountResponse.class);
            return ResponseEntity.status(idempotencyRecord.getResponseStatus()).body(cached);
        }

        // 2. Perform the real operation
        AccountResponse response = accountService.transfer(accountNumber, transferRequest, userId);

        // 3. Record the result
        idempotencyService.createRecord(
                idempotencyKey,
                userId,
                httpRequest.getRequestURI(),
                transferRequest,
                200,
                response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactionHistory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber) {
        Long userId = Long.parseLong(jwt.getSubject());
        return transactionService.findHistoryForAccount(accountNumber, userId);
    }
}
