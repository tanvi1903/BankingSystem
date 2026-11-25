package com.banking.bankingsystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        Map<String, String> validationErrors
) {
    public ErrorResponse(int status, String errorCode, String message, String path) {
        this(LocalDateTime.now(), status, errorCode, message, path, null);
    }

    public ErrorResponse(int status, String errorCode, String message, String path, Map<String, String> validationErrors) {
        this(LocalDateTime.now(), status, errorCode, message, path, validationErrors);
    }
}