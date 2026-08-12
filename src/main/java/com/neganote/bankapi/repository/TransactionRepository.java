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
    WHERE t.fromAccount.id = :accountId OR t.toAccount.id = :accountId
    ORDER BY t.occurredAt DESC
""")
    List<BankTransaction> findHistoryForAccount(@Param("accountId") Long accountId);
}
