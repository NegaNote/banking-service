package com.neganote.bankapi.service;

import com.neganote.bankapi.dto.transaction.TransactionResponse;
import com.neganote.bankapi.exception.ResourceNotFoundException;
import com.neganote.bankapi.mapper.TransactionMapper;
import com.neganote.bankapi.repository.AccountRepository;
import com.neganote.bankapi.repository.TransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public List<TransactionResponse> findHistoryForAccount(Long accountId, String username) {
        if (!accountRepository.existsByIdAndOwner_Username(accountId, username)) {
            throw new ResourceNotFoundException("Account not found");
        }

        return transactionRepository.findHistoryForAccount(accountId).stream()
                .map(TransactionMapper::toResponse)
                .toList();
    }
}
