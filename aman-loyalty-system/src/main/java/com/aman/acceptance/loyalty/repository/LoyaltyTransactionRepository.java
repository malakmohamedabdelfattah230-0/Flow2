package com.aman.acceptance.loyalty.repository;

import com.aman.acceptance.loyalty.enums.TransactionType;
import com.aman.acceptance.loyalty.model.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    boolean existsByAccount_IdAndSourceTransactionIdAndType(
            Long accountId,
            String sourceTransactionId,
            TransactionType type
    );
}
