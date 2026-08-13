package com.neganote.bankapi.dto.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
public class TransactionResponse {
    private Long id;
    private String type;
    private BigDecimal amount;
    private String fromAccountNumber;
    private String toAccountNumber;
    private String description;
    private LocalDateTime occurredAt;
}
