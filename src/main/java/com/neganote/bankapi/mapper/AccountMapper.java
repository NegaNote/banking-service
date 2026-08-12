package com.neganote.bankapi.mapper;

import com.neganote.bankapi.dto.account.AccountResponse;
import com.neganote.bankapi.entity.Account;

public class AccountMapper {
    private AccountMapper() {}

    public static AccountResponse toAccountResponse(Account account) {
        return AccountResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
