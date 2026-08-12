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
public class DepositRequest {
    @DecimalMin(value = "0.01", message = "Deposit amount must be greater than zero") @NotNull private BigDecimal amount;
}
