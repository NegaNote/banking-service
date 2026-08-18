package com.neganote.bankapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neganote.bankapi.dto.account.DepositRequest;
import com.neganote.bankapi.dto.account.OpenAccountRequest;
import com.neganote.bankapi.dto.account.TransferRequest;
import com.neganote.bankapi.dto.account.WithdrawalRequest;
import com.neganote.bankapi.entity.Account;
import com.neganote.bankapi.entity.AccountStatus;
import com.neganote.bankapi.entity.BankTransaction;
import com.neganote.bankapi.entity.TransactionType;
import com.neganote.bankapi.exception.InsufficientFundsException;
import com.neganote.bankapi.exception.InvalidTransferException;
import com.neganote.bankapi.exception.ResourceNotFoundException;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionRepository);
    }

    @Test
    void createAccountGeneratesAnActiveTwelveDigitAccountWithZeroBalance() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        var response = accountService.createAccount(42L, new OpenAccountRequest());

        assertThat(response.getAccountNumber()).matches("[0-9]{12}");
        assertThat(response.getBalance()).isEqualByComparingTo("0.00");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getOwnerId()).isEqualTo(42L);
    }

    @Test
    void createAccountRetriesWhenTheGeneratedNumberAlreadyExists() {
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true, false);

        accountService.createAccount(42L, new OpenAccountRequest());

        verify(accountRepository, org.mockito.Mockito.times(2)).existsByAccountNumber(anyString());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void findMyAccountsMapsOnlyTheOwnersAccounts() {
        Account newest = account("100000000002", 42L, "5.00");
        Account oldest = account("100000000001", 42L, "10.00");
        when(accountRepository.findByOwnerIdOrderByCreatedAtDesc(42L))
                .thenReturn(List.of(newest, oldest));

        var responses = accountService.findMyAccounts(42L);

        assertThat(responses).extracting("accountNumber").containsExactly("100000000002", "100000000001");
        assertThat(responses).extracting("balance").containsExactly(new BigDecimal("5.00"), new BigDecimal("10.00"));
    }

    @Test
    void findMyAccountRejectsAnAccountOwnedBySomeoneElse() {
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findMyAccount("100000000001", 42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found");
    }

    @Test
    void depositRecordsTheTransactionAndIncreasesTheBalance() {
        Account account = account("100000000001", 42L, "10.00");
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response =
                accountService.deposit(
                        "100000000001", new DepositRequest(new BigDecimal("2.50")), 42L);

        assertThat(response.getBalance()).isEqualByComparingTo("12.50");
        ArgumentCaptor<BankTransaction> transactionCaptor =
                ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("2.50");
        assertThat(transactionCaptor.getValue().getFromAccount()).isSameAs(account);
    }

    @Test
    void withdrawRecordsTheTransactionAndDecreasesTheBalance() {
        Account account = account("100000000001", 42L, "10.00");
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response =
                accountService.withdraw(
                        "100000000001", new WithdrawalRequest(new BigDecimal("2.50")), 42L);

        assertThat(response.getBalance()).isEqualByComparingTo("7.50");
        verify(transactionRepository).save(any(BankTransaction.class));
    }

    @Test
    void withdrawRejectsAnInactiveAccountWithoutWriting() {
        Account account = account("100000000001", 42L, "10.00");
        account.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(
                        () ->
                                accountService.withdraw(
                                        "100000000001",
                                        new WithdrawalRequest(new BigDecimal("2.50")),
                                        42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account is not active");
        verify(transactionRepository, never()).save(any(BankTransaction.class));
        verify(accountRepository, never()).save(account);
    }

    @Test
    void withdrawRejectsInsufficientFundsWithoutWriting() {
        Account account = account("100000000001", 42L, "10.00");
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(
                        () ->
                                accountService.withdraw(
                                        "100000000001",
                                        new WithdrawalRequest(new BigDecimal("10.01")),
                                        42L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account has insufficient funds");
        verify(transactionRepository, never()).save(any(BankTransaction.class));
    }

    @Test
    void transferMovesMoneyAndStoresTheDestinationAndDescription() {
        Account source = account("100000000001", 42L, "20.00");
        source.setId(1L);
        Account destination = account("100000000002", 99L, "5.00");
        destination.setId(2L);
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("100000000002"))
                .thenReturn(Optional.of(destination));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response =
                accountService.transfer(
                        "100000000001",
                        new TransferRequest(new BigDecimal("7.50"), "100000000002", "Rent"),
                        42L);

        assertThat(response.getBalance()).isEqualByComparingTo("12.50");
        assertThat(destination.getBalance()).isEqualByComparingTo("12.50");
        ArgumentCaptor<BankTransaction> transactionCaptor =
                ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        BankTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getFromAccount()).isSameAs(source);
        assertThat(transaction.getToAccount()).isSameAs(destination);
        assertThat(transaction.getDescription()).isEqualTo("Rent");
    }

    @Test
    void transferRejectsTheSameAccount() {
        Account source = account("100000000001", 42L, "20.00");
        source.setId(1L);
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("100000000001")).thenReturn(Optional.of(source));

        assertThatThrownBy(
                        () ->
                                accountService.transfer(
                                        "100000000001",
                                        new TransferRequest(new BigDecimal("1.00"), "100000000001", null),
                                        42L))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessage("Cannot transfer to the same account");
    }

    @Test
    void transferRejectsInactiveDestinationAndInsufficientSourceFunds() {
        Account source = account("100000000001", 42L, "5.00");
        source.setId(1L);
        Account destination = account("100000000002", 99L, "5.00");
        destination.setId(2L);
        destination.setStatus(AccountStatus.CLOSED);
        when(accountRepository.findByAccountNumberAndOwnerId("100000000001", 42L))
                .thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("100000000002")).thenReturn(Optional.of(destination));

        assertThatThrownBy(
                        () ->
                                accountService.transfer(
                                        "100000000001",
                                        new TransferRequest(new BigDecimal("1.00"), "100000000002", null),
                                        42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Destination account not active");

        destination.setStatus(AccountStatus.ACTIVE);
        assertThatThrownBy(
                        () ->
                                accountService.transfer(
                                        "100000000001",
                                        new TransferRequest(new BigDecimal("5.01"), "100000000002", null),
                                        42L))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Source account has insufficient funds");
    }

    private Account account(String number, Long ownerId, String balance) {
        return Account.builder()
                .accountNumber(number)
                .ownerId(ownerId)
                .balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .build();
    }
}
