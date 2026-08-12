package com.neganote.bankapi.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class WithdrawalRequest {
    @DecimalMin(value = "0.01", message = "Withdrawal amount must be greater than zero") @NotNull private BigDecimal amount;
}
