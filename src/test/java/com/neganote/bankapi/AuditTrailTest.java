package com.neganote.bankapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neganote.bankapi.audit.AuditEvent;
import com.neganote.bankapi.audit.AuditLog;
import com.neganote.bankapi.audit.AuditLogRepository;
import com.neganote.bankapi.audit.AuditService;
import com.neganote.bankapi.dto.account.AccountResponse;
import com.neganote.bankapi.entity.Account;
import com.neganote.bankapi.idempotency.IdempotencyRecordRepository;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuditTrailTest {

    private static final long USER_ID = 303L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private AuditService auditService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void successfulTransferProducesAuditRecord() throws Exception {
        // Open two accounts with a known user
        String sourceAccount = openAccount(USER_ID);
        String destAccount = openAccount(USER_ID);

        // Deposit money into source account so transfer can succeed
        postJson(
                        "/api/v2/accounts/" + sourceAccount + "/deposits",
                        USER_ID,
                        "{\"amount\":100.00}",
                        "deposit-key-1")
                .andExpect(status().isOk());

        String syntheticTraceId = "synthetic-trace-success-101";
        String transferBody =
                "{\"amount\":40.00,\"toAccountNumber\":\""
                        + destAccount
                        + "\",\"description\":\"Successful test transfer\"}";

        // Transfer between them
        mockMvc.perform(
                        post("/api/v2/accounts/" + sourceAccount + "/transfers")
                                .with(asUser(USER_ID))
                                .header("Idempotency-Key", "transfer-key-1")
                                .header("X-Trace-Id", syntheticTraceId)
                                .contentType("application/json")
                                .content(transferBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.0));

        // Assert: exactly one audit row with eventType=TRANSFER, result=SUCCESS
        List<AuditLog> transferLogs = awaitAuditLogs("TRANSFER");
        assertThat(transferLogs).hasSize(1);

        AuditLog auditLog = transferLogs.get(0);
        assertThat(auditLog.getEventType()).isEqualTo("TRANSFER");
        assertThat(auditLog.getResult()).isEqualTo("SUCCESS");
        assertThat(auditLog.getUserId()).isEqualTo(USER_ID);
        assertThat(auditLog.getAccountNumber()).isEqualTo(sourceAccount);
        assertThat(auditLog.getCounterpartyAccountNumber()).isEqualTo(destAccount);
        assertThat(auditLog.getAmount()).isEqualByComparingTo("40.00");

        // Assert: the audit row contains the request's traceId (synthetic if needed)
        assertThat(auditLog.getTraceId()).isEqualTo(syntheticTraceId);
    }

    @Test
    void declinedTransferStillProducesAuditRecord() throws Exception {
        // Open an account with $0 (and a destination account)
        String sourceAccount = openAccount(USER_ID);
        String destAccount = openAccount(USER_ID);

        String syntheticTraceId = "synthetic-trace-declined-202";
        String transferBody =
                "{\"amount\":100.00,\"toAccountNumber\":\""
                        + destAccount
                        + "\",\"description\":\"Declined transfer test\"}";

        // Attempt to transfer $100 from it
        // Assert: the response is 422
        mockMvc.perform(
                        post("/api/v2/accounts/" + sourceAccount + "/transfers")
                                .with(asUser(USER_ID))
                                .header("Idempotency-Key", "transfer-key-declined")
                                .header("X-Trace-Id", syntheticTraceId)
                                .contentType("application/json")
                                .content(transferBody))
                .andExpect(status().isUnprocessableContent());

        // Assert: an audit row exists with eventType=TRANSFER, result=DECLINED
        List<AuditLog> transferLogs = awaitAuditLogs("TRANSFER");
        assertThat(transferLogs).hasSize(1);

        AuditLog auditLog = transferLogs.get(0);
        assertThat(auditLog.getEventType()).isEqualTo("TRANSFER");
        assertThat(auditLog.getResult()).isEqualTo("DECLINED");
        assertThat(auditLog.getUserId()).isEqualTo(USER_ID);
        assertThat(auditLog.getAccountNumber()).isEqualTo(sourceAccount);
        assertThat(auditLog.getCounterpartyAccountNumber()).isEqualTo(destAccount);
        assertThat(auditLog.getAmount()).isEqualByComparingTo("100.00");
        assertThat(auditLog.getTraceId()).isEqualTo(syntheticTraceId);

        // Assert: NO money moved (verify balance)
        mockMvc.perform(get("/api/v2/accounts/" + sourceAccount).with(asUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));
        mockMvc.perform(get("/api/v2/accounts/" + destAccount).with(asUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void auditWritesPersistEvenWhenBusinessTransactionRollsBack() throws Exception {
        // Open two accounts and deposit initial funds
        String sourceAccount = openAccount(USER_ID);
        String destAccount = openAccount(USER_ID);

        postJson(
                        "/api/v2/accounts/" + sourceAccount + "/deposits",
                        USER_ID,
                        "{\"amount\":100.00}",
                        "deposit-key-3")
                .andExpect(status().isOk());

        String syntheticTraceId = "rollback-trace-303";

        // Force the transfer to throw after the AuditService.record() but inside the @Transactional
        assertThatThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        status -> {
                                            Account source =
                                                    accountRepository
                                                            .findByAccountNumber(sourceAccount)
                                                            .orElseThrow();
                                            Account dest =
                                                    accountRepository
                                                            .findByAccountNumber(destAccount)
                                                            .orElseThrow();

                                            // Business mutation inside transaction
                                            source.setBalance(
                                                    source.getBalance()
                                                            .subtract(new BigDecimal("50.00")));
                                            accountRepository.save(source);

                                            dest.setBalance(
                                                    dest.getBalance()
                                                            .add(new BigDecimal("50.00")));
                                            accountRepository.save(dest);

                                            // Record audit event inside business transaction
                                            MDC.put("traceId", syntheticTraceId);
                                            try {
                                                auditService.record(
                                                        AuditEvent.builder()
                                                                .eventType("TRANSFER")
                                                                .userId(USER_ID)
                                                                .accountNumber(sourceAccount)
                                                                .counterpartyAccountNumber(
                                                                        destAccount)
                                                                .amount(new BigDecimal("50.00"))
                                                                .result("SUCCESS")
                                                                .detail("Rollback test transfer")
                                                                .build());
                                            } finally {
                                                MDC.remove("traceId");
                                            }

                                            // Force exception after audit write inside business transaction
                                            throw new RuntimeException(
                                                    "Forced exception to trigger transaction"
                                                            + " rollback");
                                        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Forced exception to trigger transaction rollback");

        // Assert: business changes rolled back
        Account sourceAfterRollback =
                accountRepository.findByAccountNumber(sourceAccount).orElseThrow();
        Account destAfterRollback =
                accountRepository.findByAccountNumber(destAccount).orElseThrow();

        assertThat(sourceAfterRollback.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destAfterRollback.getBalance()).isEqualByComparingTo("0.00");

        // Assert: audit record present (proves REQUIRES_NEW)
        List<AuditLog> transferLogs = awaitAuditLogs("TRANSFER");
        assertThat(transferLogs).hasSize(1);

        AuditLog auditLog = transferLogs.get(0);
        assertThat(auditLog.getEventType()).isEqualTo("TRANSFER");
        assertThat(auditLog.getResult()).isEqualTo("SUCCESS");
        assertThat(auditLog.getAccountNumber()).isEqualTo(sourceAccount);
        assertThat(auditLog.getCounterpartyAccountNumber()).isEqualTo(destAccount);
        assertThat(auditLog.getTraceId()).isEqualTo(syntheticTraceId);
    }

    private String openAccount(long userId) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v2/accounts")
                                        .with(asUser(userId))
                                        .contentType("application/json")
                                        .content("{}"))
                        .andExpect(status().isOk())
                        .andReturn();
        AccountResponse response =
                jsonMapper.readValue(
                        result.getResponse().getContentAsString(), AccountResponse.class);
        return response.getAccountNumber();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(
            String path, long userId, String body, String idempotencyKey) throws Exception {
        var builder = post(path).with(asUser(userId)).contentType("application/json").content(body);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private RequestPostProcessor asUser(long userId) {
        return jwt().jwt(jwt -> jwt.subject(String.valueOf(userId)));
    }

    private List<AuditLog> awaitAuditLogs(String eventType) {
        for (int i = 0; i < 20; i++) {
            List<AuditLog> logs =
                    auditLogRepository.findAll().stream()
                            .filter(log -> eventType.equals(log.getEventType()))
                            .toList();
            if (!logs.isEmpty()) {
                return logs;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return auditLogRepository.findAll().stream()
                .filter(log -> eventType.equals(log.getEventType()))
                .toList();
    }
}