package com.neganote.bankapi.idempotency;

import com.neganote.bankapi.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The client-supplied key. Scoped per-user. */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Hash of the request body, base64-encoded SHA-256. Used to detect key reuse with different payload. */
    @Column(name = "request_hash", nullable = false, length = 100)
    private String requestHash;

    /** HTTP status of the cached response. */
    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    /** Cached response body, serialized as JSON. */
    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    /** Path being called — included so the same key can't be reused across different operations. */
    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** When this record becomes eligible for cleanup. 24 hours after createdAt. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.expiresAt = this.createdAt.plusHours(24);
    }
}
