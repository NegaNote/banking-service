package com.neganote.bankapi.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(columnNames = "account_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public-facing account number. Generated server-side. Never use the id externally. */
    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version private Long version; // optimistic locking - see Traps

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
