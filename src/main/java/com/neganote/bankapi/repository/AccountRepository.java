package com.neganote.bankapi.repository;

import com.neganote.bankapi.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByOwner_UsernameOrderByCreatedAtDesc(String username);

    Optional<Account> findByAccountNumberAndOwner_Username(String accountNumber, String username);

    boolean existsByAccountNumberAndOwner_Username(String accountNumber, String username);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
}
