package com.aman.acceptance.loyalty.service;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.aman.acceptance.loyalty.model.IdempotencyRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aman.acceptance.loyalty.enums.AccountStatus;
import com.aman.acceptance.loyalty.enums.LotStatus;
import com.aman.acceptance.loyalty.enums.ProgramStatus;
import com.aman.acceptance.loyalty.enums.TransactionStatus;
import com.aman.acceptance.loyalty.enums.TransactionType;
import com.aman.acceptance.loyalty.exception.IdempotencyConflictException;
import com.aman.acceptance.loyalty.exception.AccountFrozenException;
import com.aman.acceptance.loyalty.exception.AccountNotFoundException;
import com.aman.acceptance.loyalty.exception.DuplicateTransactionException;
import com.aman.acceptance.loyalty.exception.ProgramInactiveException;
import com.aman.acceptance.loyalty.exception.RuleNotFoundException;
import com.aman.acceptance.loyalty.model.LoyaltyAccount;
import com.aman.acceptance.loyalty.model.LoyaltyProgram;
import com.aman.acceptance.loyalty.model.LoyaltyTransaction;
import com.aman.acceptance.loyalty.model.PointsLot;
import com.aman.acceptance.loyalty.model.RuleVersion;
import com.aman.acceptance.loyalty.model.dto.BalanceDto;
import com.aman.acceptance.loyalty.model.dto.EarningRequest;
import com.aman.acceptance.loyalty.model.dto.EarningResponse;
import com.aman.acceptance.loyalty.repository.IdempotencyRecordRepository;
import com.aman.acceptance.loyalty.repository.LoyaltyAccountRepository;
import com.aman.acceptance.loyalty.repository.LoyaltyTransactionRepository;
import com.aman.acceptance.loyalty.repository.PointsLotRepository;
import com.aman.acceptance.loyalty.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EarningService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final PointsLotRepository pointsLotRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public EarningResponse earn(
            EarningRequest request,
            String idempotencyKey
    ) {
        String clientId = "pos-client";
        String endpoint = "POST /api/v1/loyalty/earnings";

        IdempotencyRecord existingRecord =
                idempotencyRecordRepository
                        .findByClientIdAndEndpointAndIdempotencyKey(
                                clientId,
                                endpoint,
                                idempotencyKey
                        )
                        .orElse(null);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to serialize earning request",
                    e
            );
        }

        String requestHash;

        try {
            requestHash = bytesToHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    requestJson.getBytes(StandardCharsets.UTF_8)
                            )
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to calculate request hash",
                    e
            );
        }


        if (existingRecord != null) {

            if (!existingRecord.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }

            try {
                return objectMapper.readValue(
                        existingRecord.getResponseBody(),
                        EarningResponse.class
                );
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to deserialize idempotency response",
                        e
                );
            }
        }

        LoyaltyAccount account = loyaltyAccountRepository
                .findByIdForUpdate(request.getAccountId())
                .orElseThrow(() ->
                        new AccountNotFoundException(request.getAccountId())
                );

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(account.getId());
        }

        boolean transactionExists =
                loyaltyTransactionRepository
                        .existsByAccount_IdAndSourceTransactionIdAndType(
                                account.getId(),
                                request.getSourceTransactionId(),
                                TransactionType.EARN
                        );

        if (transactionExists) {
            throw new DuplicateTransactionException(
                    request.getSourceTransactionId()
            );
        }

        LoyaltyProgram program = account.getProgram();

        if (program.getStatus() == ProgramStatus.INACTIVE) {
            throw new ProgramInactiveException(program.getId());
        }

        Long programId = program.getId();

        RuleVersion rule = ruleVersionRepository
                .findEffectiveRule(
                        programId,
                        request.getTransactionTime()
                )
                .orElseThrow(() ->
                        new RuleNotFoundException(
                                programId,
                                request.getTransactionTime()
                        )
                );

        BigDecimal earnedPointsDecimal =
                request.getAmount()
                        .getValue()
                        .multiply(rule.getEarningRate());

        Integer earnedPoints =
                earnedPointsDecimal
                        .setScale(
                                0,
                                mapToJavaRoundingMode(rule.getRoundingMode())
                        )
                        .intValue();

        LoyaltyTransaction transaction =
                LoyaltyTransaction.builder()
                        .account(account)
                        .type(TransactionType.EARN)
                        .sourceTransactionId(
                                request.getSourceTransactionId()
                        )
                        .points(earnedPoints)
                        .moneyAmount(request.getAmount().getValue())
                        .status(TransactionStatus.COMMITTED)
                        .idempotencyKey(idempotencyKey)
                        .build();

        loyaltyTransactionRepository.save(transaction);

        LocalDateTime unlockAt =
                request.getTransactionTime()
                        .plusDays(program.getLockDays());

        LocalDateTime expiresAt =
                request.getTransactionTime()
                        .plusDays(program.getExpiryDays());

        PointsLot pointsLot =
                PointsLot.builder()
                        .account(account)
                        .earningTransaction(transaction)
                        .originalPoints(earnedPoints)
                        .remainingPoints(earnedPoints)
                        .unlockAt(unlockAt)
                        .expiresAt(expiresAt)
                        .status(LotStatus.LOCKED)
                        .build();

        pointsLotRepository.save(pointsLot);

        account.setLockedPoints(
                account.getLockedPoints() + earnedPoints
        );

        loyaltyAccountRepository.save(account);

        BalanceDto balance = new BalanceDto(
                account.getAvailablePoints(),
                account.getLockedPoints(),
                account.getReservedPoints(),
                account.getTotalOwned()
        );

        EarningResponse response = new EarningResponse(
                transaction.getId(),
                request.getSourceTransactionId(),
                earnedPoints,
                pointsLot.getStatus(),
                pointsLot.getUnlockAt(),
                pointsLot.getExpiresAt(),
                rule.getId(),
                balance
        );

        try {
            String responseBody = objectMapper.writeValueAsString(response);

            IdempotencyRecord record = IdempotencyRecord.builder()
                    .clientId(clientId)
                    .endpoint(endpoint)
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .responseBody(responseBody)
                    .build();

            idempotencyRecordRepository.save(record);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to save idempotency response",
                    e
            );
        }

        return response;
    }

    private java.math.RoundingMode mapToJavaRoundingMode(
            com.aman.acceptance.loyalty.enums.RoundingMode mode
    ) {
        return switch (mode) {
            case FLOOR -> java.math.RoundingMode.FLOOR;
        };
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }




}