package com.aman.acceptance.loyalty.exception;

import com.aman.acceptance.loyalty.model.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoyaltyException.class)
    public ResponseEntity<ApiErrorResponse> handleLoyaltyException(
            LoyaltyException exception
    ) {

        ApiErrorResponse response = ApiErrorResponse.builder()
                .success(false)
                .error(
                        ApiErrorResponse.ErrorDetail.builder()
                                .code(exception.getCode())
                                .message(exception.getMessage())
                                .retryable(exception.isRetryable())
                                .details(exception.getDetails())
                                .build()
                )
                .meta(
                        ApiErrorResponse.Meta.builder()
                                .correlationId(null)
                                .timestamp(LocalDateTime.now())
                                .build()
                )
                .build();

        return ResponseEntity
                .status(exception.getStatus())
                .body(response);
    }
}