package com.neganote.bankapi.dto.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private String accountNumber;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;
}
