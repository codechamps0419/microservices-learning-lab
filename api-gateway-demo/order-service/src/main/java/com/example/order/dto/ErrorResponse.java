package com.example.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/*
 * Consistent error response body returned by GlobalExceptionHandler
 * for every non-2xx response from order-service.
 *
 * WHY A CONSISTENT ERROR SHAPE MATTERS:
 * Without a standard error body, every exception produces a different
 * JSON structure (or Spring's default /error page). API consumers cannot
 * reliably parse error responses — they have to guess the shape.
 *
 * With this DTO, every error from order-service looks like:
 * {
 *   "timestamp":     "2024-01-15T10:30:00",
 *   "status":        422,
 *   "error":         "Unprocessable Entity",
 *   "message":       "Insufficient stock available for product: ...",
 *   "correlationId": "abc-123"   // present if available
 * }
 *
 * @JsonInclude(NON_NULL) — omits correlationId from the JSON if it is null.
 * This keeps the response clean for requests that arrive without a correlation ID.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String correlationId;

    public ErrorResponse(int status, String error, String message, String correlationId) {
        this.timestamp     = LocalDateTime.now();
        this.status        = status;
        this.error         = error;
        this.message       = message;
        this.correlationId = correlationId;
    }

    public LocalDateTime getTimestamp()     { return timestamp; }
    public int           getStatus()        { return status; }
    public String        getError()         { return error; }
    public String        getMessage()       { return message; }
    public String        getCorrelationId() { return correlationId; }
}
