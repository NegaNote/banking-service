package com.neganote.bankapi.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByOccurredAtDesc(Long userId);

    List<AuditLog> findByTraceIdOrderByOccurredAtAsc(String traceId);

    // Deliberately override the inherited mutators to forbid them.
    @Override
    default void delete(AuditLog entity) {
        throw new UnsupportedOperationException("Audit records cannot be deleted");
    }

    @Override
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("Audit records cannot be deleted");
    }
}
