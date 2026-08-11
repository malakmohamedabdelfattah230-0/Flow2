package com.aman.acceptance.loyalty.controller;

import com.aman.acceptance.loyalty.model.dto.EarningRequest;
import com.aman.acceptance.loyalty.model.dto.EarningResponse;
import com.aman.acceptance.loyalty.service.EarningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loyalty/earnings")
@RequiredArgsConstructor
public class EarningController {

    private final EarningService earningService;

    @PostMapping
    public ResponseEntity<EarningResponse> earn(
            @Valid @RequestBody EarningRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        EarningResponse response =
                earningService.earn(request, idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}