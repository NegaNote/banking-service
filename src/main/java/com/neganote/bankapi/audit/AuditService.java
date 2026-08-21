package com.neganote.bankapi.audit;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    /**
     * Record an audit event. Runs in a NEW transaction so audit writes commit
     * even if the calling business transaction rolls back.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        AuditLog log =
                AuditLog.builder()
                        .eventType(event.eventType())
                        .userId(event.userId())
                        .accountNumber(event.accountNumber())
                        .counterpartyAccountNumber(event.counterpartyAccountNumber())
                        .amount(event.amount())
                        .result(event.result())
                        .requestId(MDC.get("requestId"))
                        .traceId(MDC.get("traceId"))
                        .ipAddress(event.ipAddress())
                        .userAgent(event.userAgent())
                        .detail(event.detail())
                        .build();
        repository.save(log);
    }
}
