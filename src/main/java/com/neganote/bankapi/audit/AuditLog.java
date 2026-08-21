package com.neganote.bankapi.audit;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.*;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "counterparty_account_number", length = 20)
    private String counterpartyAccountNumber;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String result; // SUCCESS, DECLINED, FAILED

    @Column(name = "request_id", length = 40)
    private String requestId;

    @Column(name = "trace_id", length = 40)
    private String traceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(length = 1000)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void onCreate() {
        this.occurredAt = LocalDateTime.now(ZoneId.of("UTC"));
    }

    // NO @PreUpdate. NO setters except via Builder. This entity, once persisted, never changes.
}
