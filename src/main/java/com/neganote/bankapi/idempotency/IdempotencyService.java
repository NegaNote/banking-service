package com.neganote.bankapi.idempotency;

import com.neganote.bankapi.entity.User;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class IdempotencyService {

    private final JsonMapper jsonMapper;
    private final IdempotencyRecordRepository repository;

    /**
     * Look up an existing record for this (key, user, path). If found, return it.
     * If the request body's hash differs from the stored hash, throw IdempotencyConflictException.
     * Returns Optional.empty() if no record exists yet.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findExisting(
            String key, User user, String path, Object requestBody) {
        var hash = hashBody(requestBody);
        Optional<IdempotencyRecord> recordOpt =
                repository.findByIdempotencyKeyAndUserIdAndRequestPath(key, user.getId(), path);
        if (recordOpt.isPresent()) {
            var idempotencyRecord = recordOpt.get();
            if (!idempotencyRecord.getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException(
                        "Idempotency key already used with a different request body");
            }
            if (idempotencyRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
                repository.delete(idempotencyRecord);
                return Optional.empty();
            }
        }
        return recordOpt;
    }

    /**
     * Persist a new idempotency record AFTER the operation has succeeded.
     * Called from the controller after the service-layer call returns.
     */
    public void createRecord(
            String key,
            User user,
            String path,
            Object requestBody,
            int responseStatus,
            Object responseBody) {
        IdempotencyRecord idempotencyRecord =
                IdempotencyRecord.builder()
                        .idempotencyKey(key)
                        .user(user)
                        .requestPath(path)
                        .requestHash(hashBody(requestBody))
                        .responseStatus(responseStatus)
                        .responseBody(jsonMapper.writeValueAsString(responseBody))
                        .build();
        repository.save(idempotencyRecord);
    }

    private String hashBody(Object body) {
        byte[] jsonBytes = jsonMapper.writeValueAsBytes(body);
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(jsonBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash request body", e);
        }

        return Base64.getEncoder().encodeToString(hash);
    }
}
