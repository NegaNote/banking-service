package com.neganote.bankapi.controller.v2;

import com.neganote.bankapi.audit.AuditEvent;
import com.neganote.bankapi.audit.AuditService;
import com.neganote.bankapi.dto.account.*;
import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.exception.InsufficientFundsException;
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
    private final AuditService auditService;

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
        String ip = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
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

        try {

            // 2. Perform the real operation
            AccountResponse response =
                    accountService.deposit(accountNumber, depositRequest, userId);

            auditService.record(
                    AuditEvent.builder()
                            .eventType("DEPOSIT")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(null)
                            .amount(depositRequest.getAmount())
                            .result("SUCCESS")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail("To: " + accountNumber)
                            .build());

            // 3. Record the result
            idempotencyService.createRecord(
                    idempotencyKey,
                    userId,
                    httpRequest.getRequestURI(),
                    depositRequest,
                    200,
                    response);

            return ResponseEntity.ok(response);

        } catch (InsufficientFundsException e) {
            auditService.record(
                    AuditEvent.builder()
                            .eventType("DEPOSIT")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(null)
                            .amount(depositRequest.getAmount())
                            .result("DECLINED")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail(e.getMessage())
                            .build());
            throw e;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For is a comma-separated chain; the leftmost is the client
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/{accountNumber}/withdrawals")
    public ResponseEntity<AccountResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawalRequest withdrawalRequest,
            HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        String ip = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
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

        try {
            AccountResponse response =
                    accountService.withdraw(accountNumber, withdrawalRequest, userId);

            auditService.record(
                    AuditEvent.builder()
                            .eventType("WITHDRAWAL")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(null)
                            .amount(withdrawalRequest.getAmount())
                            .result("SUCCESS")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail("From: " + accountNumber)
                            .build());

            idempotencyService.createRecord(
                    idempotencyKey,
                    userId,
                    httpRequest.getRequestURI(),
                    withdrawalRequest,
                    200,
                    response);

            return ResponseEntity.ok(response);
        } catch (InsufficientFundsException e) {
            auditService.record(
                    AuditEvent.builder()
                            .eventType("WITHDRAWAL")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(null)
                            .amount(withdrawalRequest.getAmount())
                            .result("DECLINED")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail(e.getMessage())
                            .build());
            throw e;
        }
    }

    @PostMapping("/{accountNumber}/transfers")
    public ResponseEntity<AccountResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountNumber,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest transferRequest,
            HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(jwt.getSubject());
        String ip = clientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
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
        try {
            AccountResponse response =
                    accountService.transfer(accountNumber, transferRequest, userId);

            auditService.record(
                    AuditEvent.builder()
                            .eventType("TRANSFER")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(transferRequest.getToAccountNumber())
                            .amount(transferRequest.getAmount())
                            .result("SUCCESS")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail(
                                    "From: "
                                            + accountNumber
                                            + " To: "
                                            + transferRequest.getToAccountNumber())
                            .build());

            idempotencyService.createRecord(
                    idempotencyKey,
                    userId,
                    httpRequest.getRequestURI(),
                    transferRequest,
                    200,
                    response);

            return ResponseEntity.ok(response);
        } catch (InsufficientFundsException e) {
            auditService.record(
                    AuditEvent.builder()
                            .eventType("TRANSFER")
                            .userId(userId)
                            .accountNumber(accountNumber)
                            .counterpartyAccountNumber(transferRequest.getToAccountNumber())
                            .amount(transferRequest.getAmount())
                            .result("DECLINED")
                            .ipAddress(ip)
                            .userAgent(ua)
                            .detail(e.getMessage())
                            .build());
            throw e;
        }
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> getTransactionHistory(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String accountNumber) {
        Long userId = Long.parseLong(jwt.getSubject());
        return transactionService.findHistoryForAccount(accountNumber, userId);
    }
}
