package com.neganote.bankapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.neganote.bankapi.entity.Account;
import com.neganote.bankapi.entity.BankTransaction;
import com.neganote.bankapi.entity.TransactionType;
import com.neganote.bankapi.exception.ResourceNotFoundException;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;

    @Test
    void historyMapsTransactionsForAnOwnedAccount() {
        Account account =
                Account.builder().accountNumber("100000000001").ownerId(42L).build();
        BankTransaction transaction =
                BankTransaction.builder()
                        .id(1L)
                        .type(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("10.00"))
                        .fromAccount(account)
                        .occurredAt(LocalDateTime.now())
                        .build();
        when(accountRepository.existsByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(true);
        when(transactionRepository.findHistoryForAccount("100000000001"))
                .thenReturn(List.of(transaction));

        var response =
                new TransactionService(transactionRepository, accountRepository)
                        .findHistoryForAccount("100000000001", 42L);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getType()).isEqualTo("DEPOSIT");
        assertThat(response.getFirst().getFromAccountNumber()).isEqualTo("100000000001");
        assertThat(response.getFirst().getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void historyRejectsAnAccountNotOwnedByTheCaller() {
        when(accountRepository.existsByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                new TransactionService(transactionRepository, accountRepository)
                                        .findHistoryForAccount("100000000001", 42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found");
    }
}
