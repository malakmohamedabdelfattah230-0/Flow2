package com.aman.acceptance.loyalty.model.dto;
import com.aman.acceptance.loyalty.enums.LotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EarningResponse {
    private Long loyaltyTransactionId;

    private String sourceTransactionId;

    private Integer earnedPoints;

    private LotStatus pointsStatus;

    private LocalDateTime unlockAt;

    private LocalDateTime expiresAt;

    private Long appliedRuleVersion;

    private BalanceDto balance;
}
