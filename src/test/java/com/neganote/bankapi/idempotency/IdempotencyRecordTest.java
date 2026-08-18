package com.neganote.bankapi.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    @Test
    void prePersistSetsCreationAndTwentyFourHourExpirationTimes() {
        IdempotencyRecord record = new IdempotencyRecord();

        record.onCreate();

        assertThat(record.getCreatedAt()).isNotNull();
        assertThat(Duration.between(record.getCreatedAt(), record.getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));
        assertThat(record.getExpiresAt()).isAfter(LocalDateTime.now());
    }
}
