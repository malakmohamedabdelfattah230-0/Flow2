package com.aman.acceptance.loyalty.model.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EarningRequest {
    @NotNull
    private Long accountId;

    @NotBlank
    private String sourceTransactionId;

    @NotNull
    @Valid
    private MoneyDto amount;

    @NotNull
    private LocalDateTime transactionTime;

    @NotBlank
    private String channel;
}
