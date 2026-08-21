package com.neganote.bankapi.audit;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record AuditEvent(
        String eventType,
        Long userId,
        String accountNumber,
        String counterpartyAccountNumber,
        BigDecimal amount,
        String result,
        String ipAddress,
        String userAgent,
        String detail) {}
