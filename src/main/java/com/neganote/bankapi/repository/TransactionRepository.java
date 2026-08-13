package com.neganote.bankapi.repository;

import com.neganote.bankapi.entity.BankTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {
    // History for one account - regardless of whether it was the source or destination
    @Query(
"""
    SELECT t FROM BankTransaction t
JOIN t.fromAccount fromAccount
LEFT JOIN t.toAccount toAccount
WHERE fromAccount.accountNumber = :accountNumber OR toAccount.accountNumber = :accountNumber
ORDER BY t.occurredAt DESC
""")
    List<BankTransaction> findHistoryForAccount(@Param("accountNumber") String accountNumber);
}
