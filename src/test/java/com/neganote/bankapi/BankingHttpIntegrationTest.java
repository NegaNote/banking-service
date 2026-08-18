package com.neganote.bankapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neganote.bankapi.dto.account.AccountResponse;
import com.neganote.bankapi.idempotency.IdempotencyRecordRepository;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class BankingHttpIntegrationTest {

    private static final long FIRST_USER = 101L;
    private static final long SECOND_USER = 202L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        // Delete dependent rows first because transactions reference accounts.
        idempotencyRecordRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void accountEndpointsRequireAuthenticationAndRejectMalformedBearerTokens() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void v1AndV2ExposeEquivalentAccountDataButOnlyV1HasDeprecationHeaders() throws Exception {
        String firstAccount = openAccount("/api/v1/accounts", FIRST_USER);
        String secondAccount = openAccount("/api/v2/accounts", FIRST_USER);

        MvcResult v1List =
                mockMvc.perform(get("/api/v1/accounts").with(asUser(FIRST_USER)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Deprecation", "true"))
                        .andExpect(header().exists("Sunset"))
                        .andExpect(
                                header().string(
                                                "Link",
                                                "</api/v2/accounts>; rel=\"successor-version\""))
                        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                        .andReturn();

        MvcResult v2List =
                mockMvc.perform(get("/api/v2/accounts").with(asUser(FIRST_USER)))
                        .andExpect(status().isOk())
                        .andExpect(header().doesNotExist("Deprecation"))
                        .andExpect(header().doesNotExist("Sunset"))
                        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                        .andReturn();

        assertThat(jsonMapper.readTree(v1List.getResponse().getContentAsString()))
                .isEqualTo(jsonMapper.readTree(v2List.getResponse().getContentAsString()));
        assertThat(v1List.getResponse().getContentAsString())
                .contains(firstAccount)
                .contains(secondAccount);

        mockMvc.perform(get("/api/v1/accounts/" + firstAccount).with(asUser(FIRST_USER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "true"))
                .andExpect(jsonPath("$.accountNumber").value(firstAccount));
        mockMvc.perform(get("/api/v2/accounts/" + firstAccount).with(asUser(FIRST_USER)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(jsonPath("$.accountNumber").value(firstAccount));
    }

    @Test
    void v1OperationsEnforceOwnershipAndBusinessRules() throws Exception {
        String source = openAccount("/api/v1/accounts", FIRST_USER);
        String destination = openAccount("/api/v1/accounts", FIRST_USER);
        String otherUsersAccount = openAccount("/api/v1/accounts", SECOND_USER);

        postJson("/api/v1/accounts/" + source + "/deposits", FIRST_USER, "{\"amount\":100.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));
        postJson("/api/v1/accounts/" + source + "/withdrawals", FIRST_USER, "{\"amount\":25.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(75.0));

        postJson("/api/v1/accounts/" + source + "/withdrawals", FIRST_USER, "{\"amount\":1000.00}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Account has insufficient funds"));

        postJson(
                        "/api/v1/accounts/" + source + "/transfers",
                        FIRST_USER,
                        "{\"amount\":1.00,\"toAccountNumber\":\"" + source + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot transfer to the same account"));

        postJson(
                        "/api/v1/accounts/" + otherUsersAccount + "/deposits",
                        FIRST_USER,
                        "{\"amount\":1.00}")
                .andExpect(status().isNotFound());
        postJson(
                        "/api/v1/accounts/" + otherUsersAccount + "/withdrawals",
                        FIRST_USER,
                        "{\"amount\":1.00}")
                .andExpect(status().isNotFound());
        postJson(
                        "/api/v1/accounts/" + otherUsersAccount + "/transfers",
                        FIRST_USER,
                        "{\"amount\":1.00,\"toAccountNumber\":\"" + destination + "\"}")
                .andExpect(status().isNotFound());

        postJson(
                        "/api/v1/accounts/" + source + "/transfers",
                        FIRST_USER,
                        "{\"amount\":50.00,\"toAccountNumber\":\""
                                + destination
                                + "\",\"description\":\"Internal transfer\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.0));

        mockMvc.perform(get("/api/v1/accounts/" + destination).with(asUser(FIRST_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.0));

        MvcResult v1History =
                mockMvc.perform(
                                get("/api/v1/accounts/" + source + "/transactions")
                                        .with(asUser(FIRST_USER)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
                        .andExpect(jsonPath("$[0].fromAccountNumber").value(source))
                        .andReturn();
        MvcResult v2History =
                mockMvc.perform(
                                get("/api/v2/accounts/" + source + "/transactions")
                                        .with(asUser(FIRST_USER)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(jsonMapper.readTree(v1History.getResponse().getContentAsString()))
                .isEqualTo(jsonMapper.readTree(v2History.getResponse().getContentAsString()));
    }

    @Test
    void v2WritesRequireIdempotencyKeysAndReplayOnlyOneOperation() throws Exception {
        String source = openAccount("/api/v2/accounts", FIRST_USER);
        String destination = openAccount("/api/v2/accounts", FIRST_USER);

        postJson("/api/v2/accounts/" + source + "/deposits", FIRST_USER, "{\"amount\":100.00}")
                .andExpect(status().isBadRequest());

        String depositPath = "/api/v2/accounts/" + source + "/deposits";
        MvcResult firstDeposit =
                postJson(depositPath, FIRST_USER, "{\"amount\":100.00}", "deposit-key")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.balance").value(100.0))
                        .andReturn();
        String depositResponse = firstDeposit.getResponse().getContentAsString();
        for (int retry = 0; retry < 3; retry++) {
            // A replay must return the cached body and must not execute the operation again.
            MvcResult replay =
                    postJson(depositPath, FIRST_USER, "{\"amount\":100.00}", "deposit-key")
                            .andExpect(status().isOk())
                            .andReturn();
            assertThat(replay.getResponse().getContentAsString()).isEqualTo(depositResponse);
        }

        postJson(depositPath, FIRST_USER, "{\"amount\":101.00}", "deposit-key")
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Idempotency key already used with a different request"
                                                + " body"));

        String withdrawalPath = "/api/v2/accounts/" + source + "/withdrawals";
        MvcResult firstWithdrawal =
                postJson(withdrawalPath, FIRST_USER, "{\"amount\":10.00}", "withdrawal-key")
                        .andExpect(status().isOk())
                        .andReturn();
        postJson(withdrawalPath, FIRST_USER, "{\"amount\":10.00}", "withdrawal-key")
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .isEqualTo(
                                                firstWithdrawal
                                                        .getResponse()
                                                        .getContentAsString()));

        String transferPath = "/api/v2/accounts/" + source + "/transfers";
        String transferBody =
                "{\"amount\":15.00,\"toAccountNumber\":\""
                        + destination
                        + "\",\"description\":\"Idempotent transfer\"}";
        MvcResult firstTransfer =
                postJson(transferPath, FIRST_USER, transferBody, "transfer-key")
                        .andExpect(status().isOk())
                        .andReturn();
        postJson(transferPath, FIRST_USER, transferBody, "transfer-key")
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .isEqualTo(
                                                firstTransfer.getResponse().getContentAsString()));

        mockMvc.perform(get("/api/v2/accounts/" + source).with(asUser(FIRST_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(75.0));
        mockMvc.perform(get("/api/v2/accounts/" + destination).with(asUser(FIRST_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(15.0));
        assertThat(transactionRepository.count()).isEqualTo(3);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(3);
        assertThat(
                        idempotencyRecordRepository.findByIdempotencyKeyAndUserIdAndRequestPath(
                                "deposit-key", FIRST_USER, depositPath))
                .get()
                .satisfies(
                        record -> {
                            assertThat(record.getRequestHash()).isNotBlank();
                            assertThat(record.getResponseStatus()).isEqualTo(200);
                            assertThat(record.getExpiresAt()).isNotNull();
                        });
    }

    @Test
    void v2WriteValidationReturnsFieldErrors() throws Exception {
        String account = openAccount("/api/v2/accounts", FIRST_USER);

        postJson(
                        "/api/v2/accounts/" + account + "/deposits",
                        FIRST_USER,
                        "{\"amount\":0.00}",
                        "invalid-amount")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").isNotEmpty());

        postJson(
                        "/api/v2/accounts/" + account + "/transfers",
                        FIRST_USER,
                        "{\"amount\":1.00,\"toAccountNumber\":\"100000000001\",\"description\":\""
                                + "a".repeat(256)
                                + "\"}",
                        "invalid-description")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").isNotEmpty());
    }

    private String openAccount(String path, long userId) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(path)
                                        .with(asUser(userId))
                                        .contentType("application/json")
                                        .content("{}"))
                        .andExpect(status().isOk())
                        .andReturn();
        AccountResponse response =
                jsonMapper.readValue(
                        result.getResponse().getContentAsString(), AccountResponse.class);
        assertThat(response.getAccountNumber()).matches("[0-9]{12}");
        return response.getAccountNumber();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(
            String path, long userId, String body) throws Exception {
        return postJson(path, userId, body, null);
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
}
