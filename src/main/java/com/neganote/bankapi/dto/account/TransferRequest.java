package com.neganote.bankapi.dto.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class TransferRequest {
    @DecimalMin(value = "0.01", message = "Transfer amount must be greater than zero") @NotNull private BigDecimal amount;

    @NotBlank private String toAccountNumber;

    @Size(max = 255, message = "Description must not exceed 255 characters") private String description;
}
