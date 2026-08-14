package com.neganote.bankapi.repository;

import com.neganote.bankapi.entity.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndRequestPath(
            String idempotencyKey, Long userId, String requestPath);
}
