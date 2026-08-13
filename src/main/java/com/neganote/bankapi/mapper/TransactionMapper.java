package com.neganote.bankapi.mapper;

import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.entity.BankTransaction;

public class TransactionMapper {
    private TransactionMapper() {}

    public static TransactionResponse toResponse(BankTransaction transaction) {
        TransactionResponse response =
                TransactionResponse.builder()
                        .id(transaction.getId())
                        .type(transaction.getType().name())
                        .amount(transaction.getAmount())
                        .fromAccountNumber(transaction.getFromAccount().getAccountNumber())
                        .description(transaction.getDescription())
                        .occurredAt(transaction.getOccurredAt())
                        .build();
        if (transaction.getToAccount() != null) {
            response.setToAccountNumber(transaction.getToAccount().getAccountNumber());
        }
        return response;
    }
}
