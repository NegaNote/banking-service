package com.neganote.bankapi.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neganote.bankapi.dto.account.DepositRequest;
import com.neganote.bankapi.dto.account.AccountResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(JsonMapper.builder().build(), repository);
    }

    @Test
    void missingKeyRecordReturnsEmpty() {
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath("key", 42L, "/deposit"))
                .thenReturn(Optional.empty());

        assertThat(
                        service.findExisting(
                                "key",
                                42L,
                                "/deposit",
                                new DepositRequest(new BigDecimal("10.00"))))
                .isEmpty();
    }

    @Test
    void matchingRequestReturnsTheCachedRecord() {
        DepositRequest request = new DepositRequest(new BigDecimal("10.00"));
        IdempotencyRecord record =
                recordFor(request, LocalDateTime.now().plusHours(1));
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath("key", 42L, "/deposit"))
                .thenReturn(Optional.of(record));

        assertThat(service.findExisting("key", 42L, "/deposit", request))
                .containsSame(record);
        verify(repository, never()).delete(any(IdempotencyRecord.class));
    }

    @Test
    void differentRequestBodyRaisesAConflict() {
        IdempotencyRecord record =
                recordFor(new DepositRequest(new BigDecimal("10.00")), LocalDateTime.now().plusHours(1));
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath("key", 42L, "/deposit"))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(
                        () ->
                                service.findExisting(
                                        "key",
                                        42L,
                                        "/deposit",
                                        new DepositRequest(new BigDecimal("11.00"))))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key already used with a different request body");
    }

    @Test
    void expiredRecordIsDeletedAndTreatedAsNew() {
        DepositRequest request = new DepositRequest(new BigDecimal("10.00"));
        IdempotencyRecord record = recordFor(request, LocalDateTime.now().minusSeconds(1));
        when(repository.findByIdempotencyKeyAndUserIdAndRequestPath("key", 42L, "/deposit"))
                .thenReturn(Optional.of(record));

        assertThat(service.findExisting("key", 42L, "/deposit", request)).isEmpty();
        verify(repository).delete(record);
    }

    @Test
    void createRecordStoresTheRequestHashStatusAndSerializedResponse() {
        DepositRequest request = new DepositRequest(new BigDecimal("10.00"));
        AccountResponse response =
                AccountResponse.builder()
                        .accountNumber("100000000001")
                        .balance(new BigDecimal("10.00"))
                        .status("ACTIVE")
                        .build();

        service.createRecord("key", 42L, "/deposit", request, 200, response);

        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        IdempotencyRecord saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo("key");
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getRequestPath()).isEqualTo("/deposit");
        assertThat(saved.getRequestHash()).isNotBlank();
        assertThat(saved.getResponseStatus()).isEqualTo(200);
        assertThat(saved.getResponseBody()).contains("\"accountNumber\":\"100000000001\"");
    }

    private IdempotencyRecord recordFor(DepositRequest request, LocalDateTime expiresAt) {
        IdempotencyService hashService =
                new IdempotencyService(JsonMapper.builder().build(), repository);
        hashService.createRecord(
                "key",
                42L,
                "/deposit",
                request,
                200,
                AccountResponse.builder().accountNumber("100000000001").build());
        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).save(captor.capture());
        IdempotencyRecord record = captor.getValue();
        record.setExpiresAt(expiresAt);
        return record;
    }
}
