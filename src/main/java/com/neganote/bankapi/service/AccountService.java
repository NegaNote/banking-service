package com.neganote.bankapi.service;

import com.neganote.bankapi.dto.account.AccountResponse;
import com.neganote.bankapi.entity.Account;
import com.neganote.bankapi.entity.AccountStatus;
import com.neganote.bankapi.exception.ResourceNotFoundException;
import com.neganote.bankapi.mapper.AccountMapper;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public static Random RNG_SOURCE = new Random();

    public AccountResponse createAccount(String username) {
        String newAccountNumber = generateAccountNumber();
        while (accountRepository.existsByAccountNumber(newAccountNumber)) {
            newAccountNumber = generateAccountNumber();
        }
        Account newAccount =
                Account.builder()
                        .accountNumber(newAccountNumber)
                        .balance(new BigDecimal("0.00"))
                        .status(AccountStatus.ACTIVE)
                        .owner(
                                userRepository
                                        .findByUsername(username)
                                        .orElseThrow(
                                                () ->
                                                        new ResourceNotFoundException(
                                                                "User not found")))
                        .build();

        accountRepository.save(newAccount);

        return AccountMapper.toAccountResponse(newAccount);
    }

    private String generateAccountNumber() {
        StringBuilder accountNumber = new StringBuilder();

        String digits = "0123456789";
        for (int i = 0; i < 12; i++) {
            int index = RNG_SOURCE.nextInt(digits.length());
            accountNumber.append(digits.charAt(index));
        }

        return accountNumber.toString();
    }
}
